//
//   Copyright 2020  SenX S.A.S.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.geoxp.oss.jarjar.org.bouncycastle.util.Arrays;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.HybridBinarizer;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import processing.core.PImage;

public class BARCODETO extends NamedWarpScriptFunction implements WarpScriptStackFunction {
    
  private static final String FORMAT = "format";
  private static final String TIMESTAMP = "timestamp";
  private static final String TEXT = "text";
  private static final String RESULTPOINTS = "resultpoints";
  private static final String METADATA = "metadata";
  private static final String RAWBYTES = "rawbytes";
  private static final String NUMBITS = "numbits";
  
  public BARCODETO(String name) {
    super(name);
  }
  
  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {
    
    MultiFormatReader READER = new MultiFormatReader();

    Object top = stack.pop();
    
    Map<Object,Object> params = new HashMap<Object,Object>();
    
    if (top instanceof Map) {
      params = (Map<Object,Object>) top;
      top = stack.pop();
    }
    
    if (!(top instanceof PImage)) {
      throw new WarpScriptException(getName() + " operates on a PGraphics or PImage instance.");
    }
    
    PImage pi = (PImage) top;
    

    //
    // Build a hint map
    //
    
    Map<DecodeHintType,Object> hints = new HashMap<DecodeHintType, Object>();
    
    for (DecodeHintType ht: DecodeHintType.values()) {
      if (params.containsKey(ht.name())) {
        if (DecodeHintType.PURE_BARCODE.equals(ht)
            || DecodeHintType.TRY_HARDER.equals(ht)
            || DecodeHintType.ASSUME_CODE_39_CHECK_DIGIT.equals(ht)
            || DecodeHintType.ASSUME_GS1.equals(ht)
            || DecodeHintType.RETURN_CODABAR_START_END.equals(ht)) {
          hints.put(ht, Boolean.TRUE.equals(params.get(ht.name())));
        } else if (DecodeHintType.CHARACTER_SET.equals(ht)) {
          hints.put(ht, String.valueOf(params.get(ht.name())));
        } else if (DecodeHintType.ALLOWED_LENGTHS.equals(ht)
            || DecodeHintType.ALLOWED_EAN_EXTENSIONS.equals(ht)) {
          Object param = params.get(ht.name());
          if (!(param instanceof List)) {
            throw new WarpScriptException(getName() + " parameter '" + ht.name() + "' must be a list of LONGs.");
          }
          int[] values = new int[((List) param).size()];
          for (int i = 0; i < values.length; i++) {
            if (!(((List) param).get(i) instanceof Long)) {
              throw new WarpScriptException(getName() + " parameter '" + ht.name() + "' must be a list of LONGs.");              
            }
            values[i] = ((Long) ((List) param).get(i)).intValue();
          }
          hints.put(ht, values);
        } else if (DecodeHintType.POSSIBLE_FORMATS.equals(ht)) {
          Object param = params.get(ht.name());
          if (!(param instanceof List)) {
            throw new WarpScriptException(getName() + " parameter '" + ht.name() + "' must be a list of STRINGs.");
          }
          List<BarcodeFormat> formats = new ArrayList<BarcodeFormat>();
          
          for (Object format: (List) param) {
            formats.add(BarcodeFormat.valueOf(format.toString()));
          }
          
          hints.put(ht, formats);
        } else {
          hints.put(ht, params.get(ht.name()));
        }
      }
    }
    
    pi.loadPixels();
    int[] pixels = Arrays.copyOf(pi.pixels, pi.pixels.length);
    LuminanceSource source = new RGBLuminanceSource(pi.width, pi.height, pixels);

    BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
    Result result = null;
    
    try {
      result = READER.decode(bitmap, hints);
    } catch (NotFoundException nfe) {     
    }
    
    Map<String,Object> decoded = new HashMap<String,Object>();
    
    if (null != result) {
      decoded.put(FORMAT, result.getBarcodeFormat().name());
      decoded.put(NUMBITS, (long) result.getNumBits());
      decoded.put(RAWBYTES, result.getRawBytes());
      decoded.put(TEXT, result.getText());
      decoded.put(TIMESTAMP, result.getTimestamp());
      ResultPoint[] points = result.getResultPoints();
      if (null != points) {
        List<List<Double>> rpoints = new ArrayList<List<Double>>(points.length);
        for (ResultPoint point: points) {
          List<Double> rp = new ArrayList<Double>(2);
          rp.add((double) point.getX());
          rp.add((double) point.getY());
          rpoints.add(rp);
        }
        decoded.put(RESULTPOINTS, rpoints);
      }      
      Map<ResultMetadataType,Object> meta = result.getResultMetadata();
      
      Map<String,Object> metadata = new HashMap<String, Object>();
      for (ResultMetadataType rmt: ResultMetadataType.values()) {
        if (meta.containsKey(rmt)) {
          // Push those as is
          if (ResultMetadataType.BYTE_SEGMENTS.equals(rmt)
              || ResultMetadataType.POSSIBLE_COUNTRY.equals(rmt)
              || ResultMetadataType.SUGGESTED_PRICE.equals(rmt)
              || ResultMetadataType.UPC_EAN_EXTENSION.equals(rmt)) {
            metadata.put(rmt.name(), meta.get(rmt));
          } else if (ResultMetadataType.ISSUE_NUMBER.equals(rmt)
              || ResultMetadataType.ORIENTATION.equals(rmt)) {
            metadata.put(rmt.name(), (long) meta.get(rmt));
          } else {
            metadata.put(rmt.name(), String.valueOf(meta.get(rmt)));
          }
        }
      }
      decoded.put(METADATA, metadata);
    }
    
    stack.push(decoded);
    
    return stack;
  }
}
