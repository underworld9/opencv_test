package org.opencv.samples.tutorial2;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.JavaCameraView;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Rect;
import org.opencv.core.Rect2d;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.ORB;
import org.opencv.imgproc.Imgproc;
import org.opencv.tracking.TrackerCSRT;
import org.opencv.tracking.TrackerMOSSE;
import org.opencv.xfeatures2d.BriefDescriptorExtractor;

import java.util.Collections;
import java.util.List;

public class Tutorial2Activity extends CameraActivity implements CvCameraViewListener2 {
    private static final String TAG = "OCVSample::Activity";

    private static final int VIEW_MODE_MOSSE = 0;
    private static final int VIEW_MODE_ORB = 1;
    private static final int VIEW_MODE_CANNY = 2;
    private static final int VIEW_MODE_CSRT = 5;

    private int mViewMode;
    private Mat mRgba;
    private Mat mIntermediateMat;
    private Mat mGray;

    private TrackerCSRT trackerCSRT;
    private TrackerMOSSE trackerMOSSE;
    private float userTouchX = -1;
    private float userTouchY = -1;
    private float boxWidth = -1;
    private float boxHeight = -1;
    private Rect2d rect;
    private TrackingStatus trackingStatus = TrackingStatus.IDLE;
    private ORB orb_Frame;
    private ORB orb_roi;
    private ORB orb;
    private MatOfKeyPoint keyPoints1;
    private MatOfKeyPoint keyPoints2;
    private Mat des1;
    private Mat des2;
    private BriefDescriptorExtractor orbExtractor;
    private DescriptorMatcher orbMatcher;
    private Mat roi;


    private enum TrackingStatus {
        IDLE,
        TRACKING
    }

    private MenuItem mItemPreviewRGBA;
    private MenuItem mItemPreviewGray;
    private MenuItem mItemPreviewCanny;
    private MenuItem mItemPreviewFeatures;

    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");

                    // Load native library after(!) OpenCV initialization
                    System.loadLibrary("mixed_sample");

                    trackerCSRT = TrackerCSRT.create();
                    trackerMOSSE = TrackerMOSSE.create();

                    keyPoints1 = new MatOfKeyPoint();
                    keyPoints2 = new MatOfKeyPoint();
                    des1 = new Mat();
                    des2 = new Mat();
//                    orb = ORB.create();

                    orb_roi = ORB.create(500, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20);
                    orb_Frame = ORB.create(2000, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20);
                    orbExtractor = BriefDescriptorExtractor.create();
                    orbMatcher = DescriptorMatcher.create(DescriptorMatcher.FLANNBASED);

                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public Tutorial2Activity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.tutorial2_surface_view);

        mOpenCvCameraView = findViewById(R.id.tutorial2_activity_surface_view);
        mOpenCvCameraView.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "called onCreateOptionsMenu");
        mItemPreviewRGBA = menu.add("Mosse");
        mItemPreviewGray = menu.add("ORB");
        mItemPreviewCanny = menu.add("Canny");
        mItemPreviewFeatures = menu.add("CSRT");
        return true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    @SuppressLint("ClickableViewAccessibility")
    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        final int viewMode = mViewMode;

        Scalar color = new Scalar(255, 0, 0, 1);
        boolean result = true;
        JavaCameraView tutorial2_activity_surface_view = findViewById(R.id.tutorial2_activity_surface_view);
//
//        frameWidth = inputFrame.rgba().width();
//        frameHeight = inputFrame.rgba().width();

        switch (viewMode) {
            case VIEW_MODE_ORB:
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();

                if (trackingStatus == TrackingStatus.IDLE && userTouchX >= 0 && userTouchY >= 0 && boxWidth >= 0 && boxHeight >= 0) {
                    trackingStatus = TrackingStatus.TRACKING;

                    rect = new Rect2d(userTouchX, userTouchY, boxWidth, boxHeight);
                    roi = mRgba.submat((int) rect.y, (int) rect.y + (int) rect.height, (int) rect.x, (int) rect.x + (int) rect.width);

                    orb_Frame.detect(mRgba, keyPoints1);
                    orb_roi.detect(roi, keyPoints2);
                    orbExtractor.compute(mRgba, keyPoints1, des1);

                } else if (trackingStatus == TrackingStatus.TRACKING) {
                    System.out.println("keyPoints1: " + keyPoints1);
                    System.out.println("des1: " + des1);
                }

                break;
            case VIEW_MODE_MOSSE:
                // input frame has RGBA format
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();

//                Mat roi = mRgba.clone();
//                if(rect != null)
//                    roi = mRgba.submat((int) rect.y, (int) rect.y + (int) rect.height, (int) rect.x, (int) rect.x + (int) rect.width);
//                Imgproc.matchTemplate(mRgba, cutImage, );
//                MatOfKeyPoint keyPoints = new MatOfKeyPoint();
//                orb.detect(mGray ,keyPoints);
//                System.out.println("keypoints: " + keyPoints);

                tutorial2_activity_surface_view.setOnTouchListener(boxTouchListener);

                if (trackingStatus == TrackingStatus.IDLE && userTouchX >= 0 && userTouchY >= 0 && boxWidth >= 0 && boxHeight >= 0) {
                    trackingStatus = TrackingStatus.TRACKING;
                    rect = new Rect2d(userTouchX, userTouchY, boxWidth, boxHeight);
                    trackerMOSSE.init(mGray, rect);
                } else if (trackingStatus == TrackingStatus.TRACKING) {
                    result = trackerMOSSE.update(mGray, rect);
                }

                if (rect != null) {
                    Rect otherRect = new Rect(((int) rect.x), (int) rect.y, (int) rect.width, (int) rect.height);
                    Imgproc.rectangle(mRgba, otherRect, color);
//                    System.out.println("X " + rect.x + " Y " + rect.y + " width: " + rect.width + " height: " + rect.height);
                }
                break;
            case VIEW_MODE_CANNY:
                // input frame has gray scale format
                mRgba = inputFrame.rgba();
                Imgproc.Canny(inputFrame.gray(), mIntermediateMat, 80, 100);
                Imgproc.cvtColor(mIntermediateMat, mRgba, Imgproc.COLOR_GRAY2RGBA, 4);
                break;
            case VIEW_MODE_CSRT:
                // input frame has RGBA format
                mRgba = inputFrame.rgba();
                mGray = inputFrame.gray();

                tutorial2_activity_surface_view.setOnTouchListener(boxTouchListener);

                if (trackingStatus == TrackingStatus.IDLE && userTouchX >= 0 && userTouchY >= 0 && boxWidth >= 0 && boxHeight >= 0) {
                    trackingStatus = TrackingStatus.TRACKING;
                    rect = new Rect2d(userTouchX, userTouchY, boxWidth, boxHeight);
                    trackerCSRT.init(mGray, rect);
                } else if (trackingStatus == TrackingStatus.TRACKING) {
                    result = trackerCSRT.update(mGray, rect);
                }

                if (rect != null) {
                    Rect otherRect = new Rect(((int) rect.x), (int) rect.y, (int) rect.width, (int) rect.height);
                    Imgproc.rectangle(mRgba, otherRect, color);
                    System.out.println("X " + rect.x + " Y " + rect.y + " width: " + rect.width + " height: " + rect.height);
                }
                break;
        }

        return mRgba;
    }

    private View.OnTouchListener boxTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {

            if (trackingStatus == TrackingStatus.IDLE) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    userTouchX = event.getX();
                    userTouchY = event.getY();
                    return true;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    boxWidth = userTouchX - event.getX();
                    boxHeight = userTouchY - event.getY();
                    boxWidth = Math.abs(boxWidth);
                    boxHeight = Math.abs(boxHeight);
                    userTouchX -= boxWidth;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    Scalar color = new Scalar(255, 0, 0, 1);
                    Rect drawRect = new Rect((int) userTouchX, (int) userTouchY, (int) userTouchX - (int) event.getX(), (int) userTouchY - (int) event.getY());
                    Imgproc.rectangle(mRgba, drawRect, color);
//                    System.out.println("X " + (int) event.getX() + " Y " + (int) event.getY() + " width: " + (userTouchX - event.getX()) + " height: " + (userTouchY - event.getY()));
                }
            } else if (trackingStatus == TrackingStatus.TRACKING) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    userTouchX = event.getX();
                    userTouchX -= boxWidth * 1.5;
                    if (userTouchX <= 0) {
                        userTouchX = 0;
                    }
                    userTouchY = event.getY();
                    userTouchY -= boxHeight * 0.6;
                    if (userTouchY <= 0) {
                        userTouchY = 0;
                    }
                    trackerMOSSE = null;
                    trackerMOSSE = TrackerMOSSE.create();
                    trackerCSRT = null;
                    trackerCSRT = TrackerCSRT.create();
                    trackingStatus = TrackingStatus.IDLE;
                }
            }
            return true;
        }
    };

    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "called onOptionsItemSelected; selected item: " + item);
        userTouchY = -1;
        userTouchX = -1;
        boxWidth = -1;
        boxHeight = -1;
        if (rect != null)
            rect.set(new double[0]);
        trackingStatus = TrackingStatus.IDLE;

        if (item == mItemPreviewRGBA) {
            mViewMode = VIEW_MODE_MOSSE;
        } else if (item == mItemPreviewGray) {
            mViewMode = VIEW_MODE_ORB;
        } else if (item == mItemPreviewCanny) {
            mViewMode = VIEW_MODE_CANNY;
        } else if (item == mItemPreviewFeatures) {
            mViewMode = VIEW_MODE_CSRT;
        }

        return true;
    }

    public native void FindFeatures(long matAddrGr, long matAddrRgba);
}
