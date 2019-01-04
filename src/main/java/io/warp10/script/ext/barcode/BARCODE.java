//
//   Copyright 2019  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.script.ext.barcode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;
import com.google.zxing.pdf417.encoder.Dimensions;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.processing.Pencode;
import processing.core.PGraphics;

public class BARCODE extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  private static final String FORMAT = "format";
  private static final String WIDTH = "width";
  private static final String HEIGHT = "height";
  private static final String CONTENT = "content";
  
  private static final String DEFAULT_FORMAT = BarcodeFormat.QR_CODE.name();
  private static final int DEFAULT_WIDTH = 50;
  private static final int DEFAULT_HEIGHT = 50;

  private static final MultiFormatWriter WRITER = new MultiFormatWriter();
  
  public BARCODE(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    Object top = stack.pop();
    
    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " expects a parameter map on top of the stack.");
    }
    
    @SuppressWarnings("unchecked")
    Map<Object,Object> params = (Map<Object,Object>) top;
  
    String format = params.containsKey(FORMAT) ? String.valueOf(params.get(FORMAT)) : DEFAULT_FORMAT;
    
    long maxpixels = ((Number) stack.getAttribute(WarpScriptStack.ATTRIBUTE_MAX_PIXELS)).longValue();
    
    BarcodeFormat bcformat = BarcodeFormat.valueOf(format);
    
    int width = params.containsKey(WIDTH) ? ((Number) params.get(WIDTH)).intValue() : DEFAULT_WIDTH;
    int height = params.containsKey(HEIGHT) ? ((Number) params.get(HEIGHT)).intValue() : DEFAULT_HEIGHT;
    
    if (width * height > maxpixels) {
      throw new WarpScriptException(getName() + " cannot generate code with more than " + maxpixels + " pixels.");
    }
    
    String content = String.valueOf(params.get(CONTENT));
    
    //
    // Build a hint map
    //
    
    Map<EncodeHintType,Object> hints = new HashMap<EncodeHintType, Object>();
    
    for (EncodeHintType ht: EncodeHintType.values()) {
      if (params.containsKey(ht.name())) {
        if (EncodeHintType.DATA_MATRIX_SHAPE.equals(ht)) {
          hints.put(ht, SymbolShapeHint.valueOf(String.valueOf(params.get(ht.name()))));
        } else if (EncodeHintType.PDF417_DIMENSIONS.equals(ht)) {
          Object val = params.get(ht.name());
          if (!(val instanceof List) || 4 != ((List) val).size()) {
            continue;
          } else {
            Dimensions dim = new Dimensions(
                ((Number) ((List) val).get(0)).intValue(),
                ((Number) ((List) val).get(1)).intValue(),
                ((Number) ((List) val).get(2)).intValue(),
                ((Number) ((List) val).get(3)).intValue()
                );
            hints.put(ht, dim);
          }
        } else {
          hints.put(ht, params.get(ht.name()));
        }
      }
    }
  
    try {
      BitMatrix matrix = WRITER.encode(content, bcformat, width, height, hints);
      
      width = matrix.getWidth();
      height = matrix.getHeight();
      
      stack.push((long) width);
      stack.push((long) height);
      stack.push("2D");
      new io.warp10.script.processing.rendering.PGraphics("").apply(stack);
      PGraphics pg = (PGraphics) stack.peek();
      
      int[] pixels = pg.pixels;
      
      for (int x = 0; x < width; x++) {
        for (int y = 0; y < height; y++) {
          pixels[y * width + x] = matrix.get(x,y) ? 0xFF000000 : 0x00000000;           
        }
      }
      
      pg.updatePixels();
      new Pencode("").apply(stack);
    } catch (WriterException we) {
      throw new WarpScriptException(getName() + " encountered an error while generating barcode.", we);
    }
    
    return stack;
  }
}
