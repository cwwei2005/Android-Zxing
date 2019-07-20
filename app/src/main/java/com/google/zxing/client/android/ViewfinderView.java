/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.zxing.client.android;

import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

  private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
  private static final long ANIMATION_DELAY = 3L;//80L  mod cww
  private static final int CURRENT_POINT_OPACITY = 0xA0;
  private static final int MAX_RESULT_POINTS = 20;
  private static final int POINT_SIZE = 6;

  private CameraManager cameraManager;
  private final Paint paint;
  private Bitmap resultBitmap;
  private final int maskColor;
  private final int resultColor;
  private final int laserColor;
  private final int resultPointColor;
  private int scannerAlpha;
  private List<ResultPoint> possibleResultPoints;
  private List<ResultPoint> lastPossibleResultPoints;


  //---------------------------------------------------------------add cww
  public int laserLinePosition=0;
  public float[] position=new float[]{0f,0.5f,1f};
  public int[] colors=new int[]{0x0002FE02,0xFF02FE02,0x0002FE02};
  private int cornerColor  = 0xFF00CC00;
  private int cornerLength  = 38;
  private int cornerWide  = 8;
  private int lineColor = 0x55ffffff;
  private int lineWide = 2;
  public LinearGradient linearGradient ;
  //----------------------------------------------------------------


  // This constructor is used when the class is built from an XML resource.
  public ViewfinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    // Initialize these once for performance rather than calling them every time in onDraw().
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    Resources resources = getResources();
    maskColor = resources.getColor(R.color.viewfinder_mask);
    resultColor = resources.getColor(R.color.result_view);
    laserColor = resources.getColor(R.color.viewfinder_laser);
    resultPointColor = resources.getColor(R.color.possible_result_points);
    scannerAlpha = 0;
    possibleResultPoints = new ArrayList<>(5);
    lastPossibleResultPoints = null;
  }

  public void setCameraManager(CameraManager cameraManager) {
    this.cameraManager = cameraManager;
  }

  @SuppressLint("DrawAllocation")
  @Override
  public void onDraw(Canvas canvas) {
    if (cameraManager == null) {
      return; // not ready yet, early draw before done configuring
    }
    Rect frame = cameraManager.getFramingRect();
    Rect previewFrame = cameraManager.getFramingRectInPreview();    
    if (frame == null || previewFrame == null) {
      return;
    }
    int width = canvas.getWidth();
    int height = canvas.getHeight();



    //--------------------------绘制4个角(前2个坐标是起点xy， 后2个是终点xy)----------------------------------add cww
    paint.setColor(cornerColor);//定义画笔的颜色
    canvas.drawRect(frame.left, frame.top, frame.left+cornerLength, frame.top+cornerWide, paint);
    canvas.drawRect(frame.left, frame.top, frame.left + cornerWide, frame.top + cornerLength, paint);
    paint.setColor(lineColor);//定义画笔的颜色
    canvas.drawRect(frame.left+cornerLength, frame.top, frame.right - cornerLength, frame.top + lineWide, paint);

    paint.setColor(cornerColor);//定义画笔的颜色
    canvas.drawRect(frame.right-cornerLength, frame.top, frame.right, frame.top+cornerWide, paint);
    canvas.drawRect(frame.right-cornerWide, frame.top, frame.right, frame.top+cornerLength, paint);
    paint.setColor(lineColor);//定义画笔的颜色
    canvas.drawRect(frame.right-lineWide, frame.top+cornerLength, frame.right, frame.bottom - cornerLength, paint);

    paint.setColor(cornerColor);//定义画笔的颜色
    canvas.drawRect(frame.left, frame.bottom-cornerWide, frame.left+cornerLength, frame.bottom, paint);
    canvas.drawRect(frame.left, frame.bottom-cornerLength, frame.left+cornerWide, frame.bottom, paint);
    paint.setColor(lineColor);//定义画笔的颜色
    canvas.drawRect(frame.left, frame.top+cornerLength, frame.left+lineWide, frame.bottom-cornerLength, paint);

    paint.setColor(cornerColor);//定义画笔的颜色
    canvas.drawRect(frame.right-cornerLength, frame.bottom-cornerWide, frame.right, frame.bottom, paint);
    canvas.drawRect(frame.right-cornerWide, frame.bottom-cornerLength, frame.right, frame.bottom, paint);
    paint.setColor(lineColor);//定义画笔的颜色
    canvas.drawRect(frame.left+cornerLength, frame.bottom-lineWide, frame.right - cornerLength, frame.bottom, paint);

//    paint.setTextSize(50);
//    paint.setColor(Color.WHITE);
//    String s = "将条码/二维码放入框内";
//    int frameWidth = frame.right - frame.left;
//    int stringWidth = s.length() * 50;
//    canvas.drawText(s, frame.left+(frameWidth-stringWidth)/2, frame.top-50, paint);
    //----------------------------------------------------------------------------------------------------



    // Draw the exterior (i.e. outside the framing rect) darkened
    paint.setColor(resultBitmap != null ? resultColor : maskColor);
    canvas.drawRect(0, 0, width, frame.top, paint);
    canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
    canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
    canvas.drawRect(0, frame.bottom + 1, width, height, paint);

    if (resultBitmap != null) {
      // Draw the opaque result bitmap over the scanning rectangle
      paint.setAlpha(CURRENT_POINT_OPACITY);
      canvas.drawBitmap(resultBitmap, null, frame, paint);
    } else {

      //-----------------------------------------------
      // Draw a red "laser scanner" line through the middle to show decoding is active
//      paint.setColor(laserColor);
//      paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
//      scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;
//      int middle = frame.height() / 2 + frame.top;
//      canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);
      //------------------------------------------------



      //------------------------------//绘制扫描线  add cww
      laserLinePosition=laserLinePosition+10;
      if(laserLinePosition>frame.height()) {
        laserLinePosition=0;
      }
      linearGradient= new LinearGradient(frame.left + 1, frame.top+laserLinePosition , frame.right -1 , frame.top +10+laserLinePosition, colors, position, Shader.TileMode.CLAMP);
      paint.setShader(linearGradient);
      canvas.drawRect(frame.left + 1, frame.top+laserLinePosition , frame.right -1 , frame.top +10+laserLinePosition, paint);
      paint.setShader(null);
      //------------------------------


      
      float scaleX = frame.width() / (float) previewFrame.width();
      float scaleY = frame.height() / (float) previewFrame.height();

      List<ResultPoint> currentPossible = possibleResultPoints;
      List<ResultPoint> currentLast = lastPossibleResultPoints;
      int frameLeft = frame.left;
      int frameTop = frame.top;
      if (currentPossible.isEmpty()) {
        lastPossibleResultPoints = null;
      } else {
        possibleResultPoints = new ArrayList<>(5);
        lastPossibleResultPoints = currentPossible;
        paint.setAlpha(CURRENT_POINT_OPACITY);
        paint.setColor(resultPointColor);
        synchronized (currentPossible) {
          for (ResultPoint point : currentPossible) {
            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              POINT_SIZE, paint);
          }
        }
      }
      if (currentLast != null) {
        paint.setAlpha(CURRENT_POINT_OPACITY / 2);
        paint.setColor(resultPointColor);
        synchronized (currentLast) {
          float radius = POINT_SIZE / 2.0f;
          for (ResultPoint point : currentLast) {
            canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                              frameTop + (int) (point.getY() * scaleY),
                              radius, paint);
          }
        }
      }

      // Request another update at the animation interval, but only repaint the laser line,
      // not the entire viewfinder mask.
      postInvalidateDelayed(ANIMATION_DELAY,
                            frame.left - POINT_SIZE,
                            frame.top - POINT_SIZE,
                            frame.right + POINT_SIZE,
                            frame.bottom + POINT_SIZE);
    }
  }

  public void drawViewfinder() {
    Bitmap resultBitmap = this.resultBitmap;
    this.resultBitmap = null;
    if (resultBitmap != null) {
      resultBitmap.recycle();
    }
    invalidate();
  }

  /**
   * Draw a bitmap with the result points highlighted instead of the live scanning display.
   *
   * @param barcode An image of the decoded barcode.
   */
  public void drawResultBitmap(Bitmap barcode) {
    resultBitmap = barcode;
    invalidate();
  }

  public void addPossibleResultPoint(ResultPoint point) {
    List<ResultPoint> points = possibleResultPoints;
    synchronized (points) {
      points.add(point);
      int size = points.size();
      if (size > MAX_RESULT_POINTS) {
        // trim it
        points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
      }
    }
  }

}
