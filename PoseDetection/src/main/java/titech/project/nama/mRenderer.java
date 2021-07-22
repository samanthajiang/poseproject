package titech.project.nama;

import android.content.Context;
import android.hardware.Camera;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;

import titech.project.nama.utils.BundleUtils;
import titech.project.nama.utils.DeviceUtils;
import titech.project.nama.utils.LogUtils;
import com.faceunity.wrapper.faceunity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class mRenderer {
    private static final String TAG = "mRenderer";
    /**
     * camera facing front/back
     */
    public static final int CAMERA_FACING_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;
    public static final int CAMERA_FACING_BACK = Camera.CameraInfo.CAMERA_FACING_BACK;

    /**
     * AI and Graphics pack
     */
    private static final String ASSETS_DIR_MODEL = "model/";
    private static final String ASSETS_DIR_GRAPHICS = "graphics/";
    private static final String ASSETS_DIR_PTA = "pta/";
    /**
     * OES anf 2D input
     */
    public static final int INPUT_TEXTURE_EXTERNAL_OES = faceunity.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE;
    public static final int INPUT_TEXTURE_2D = 0;
    /**
     * buffer input，NV21, I420, RGBA
     */
    public static final int INPUT_FORMAT_NV21_BUFFER = faceunity.FU_FORMAT_NV21_BUFFER;
    public static final int INPUT_FORMAT_I420_BUFFER = faceunity.FU_FORMAT_I420_BUFFER;
    public static final int INPUT_FORMAT_RGBA_BUFFER = faceunity.FU_FORMAT_RGBA_BUFFER;

    /**
     * Track face, human, gesture
     */
    public static final int TRACK_TYPE_FACE = faceunity.FUAITYPE_FACEPROCESSOR;
    public static final int TRACK_TYPE_HUMAN = faceunity.FUAITYPE_HUMAN_PROCESSOR;
    public static final int TRACK_TYPE_GESTURE = faceunity.FUAITYPE_HANDGESTURE;

    /**
     * face landmarks
     */
    public static final int FACE_LANDMARKS_75 = faceunity.FUAITYPE_FACELANDMARKS75;
    public static final int FACE_LANDMARKS_239 = faceunity.FUAITYPE_FACELANDMARKS239;

    /**
     * RENDER_MODE_NORMAL
     */
    public static final int RENDER_MODE_NORMAL = 1;
    public static final int RENDER_MODE_CONTROLLER = 2;
    /**
     * face number
     */
    public static final int MAX_FACE_COUNT = 8;
    public static final int MIN_FACE_COUNT = 1;

    /**
     * Human track
     */
    public static final int HUMAN_TRACK_SCENE_FULL = 1;

    public static final String KEY_AI_TYPE = "aitype";
    public static final String KEY_LANDMARKS_TYPE = "landmarks_type";

    /* Gesture bundle */
    private static final String[] GESTURE_BIND_BUNDLES = {"anim_idle.bundle", "anim_eight.bundle", "anim_fist.bundle", "anim_greet.bundle"
            , "anim_gun.bundle", "anim_heart.bundle", "anim_hold.bundle", "anim_korheart.bundle", "anim_merge.bundle",
            "anim_ok.bundle", "anim_one.bundle", "anim_palm.bundle", "anim_rock.bundle", "anim_six.bundle",
            "anim_thumb.bundle", "anim_two.bundle"};

    /* Item array */
    private int[] mItemsArray;
    private final Context mContext;
    /* IO thread Handler */
    private Handler mFuItemHandler;
    /* frame ID */
    private int mFrameId = 0;
    /* effect utils */
    private final Set<Effect> mEffectList = new HashSet<>(8);
    /* max humans */
    private int mMaxHumans = 1;
    /* max faces */
    private int mMaxFaces = MAX_FACE_COUNT;
    /*  EGLContext */
    private boolean mIsCreateEGLContext = false;
    /* INPUT_TEXTURE 2D */
    private int mInputTextureType = INPUT_TEXTURE_2D;
    /* buffer  */
    private int mInputImageFormat = 0;
    /* mInputImageOrientation 270 */
    private int mInputImageOrientation = 270;
    /* mDeviceOrientation */
    private int mDeviceOrientation = 90;
    /* createRotationMode  */
    private int mRotationMode = faceunity.FU_ROTATION_MODE_90;
    /* mCameraFacing  */
    private int mCameraFacing = CAMERA_FACING_FRONT;
    /* ArrayList */
    private final ArrayList<Runnable> mEventQueue = new ArrayList<>(16);
    /* GL thread ID */
    private long mGlThreadId;
    /* mRenderMode */
    private int mRenderMode = RENDER_MODE_NORMAL;
    /* controller  */
    private int[] mControllerBoundItems;
    /* mHumanTrackScene */
    private int mHumanTrackScene = HUMAN_TRACK_SCENE_FULL;
    /* RotatedImage */
    private final faceunity.RotatedImage mRotatedImage = new faceunity.RotatedImage();
    /* controller callback */
    private Callback mControllerCallback;
    /* sIsInited */
    private static boolean sIsInited;
    /* mRotationEuler */
    private final int[] mTongueDirection = new int[1];
    private final int[] mEmotion = new int[1];
    private final int[] mExpressionType = new int[1];
    private final float[] mRotationEuler = new float[3];

    public static void setup(Context context) {
        if (sIsInited) {
            return;
        }
        long startTime = System.currentTimeMillis();
        faceunity.fuSetLogLevel(FuLogLevel.FU_LOG_LEVEL_WARN);
        LogUtils.setLogLevel(LogUtils.DEBUG);

        faceunity.fuCreateEGLContext();
        LogUtils.info(TAG, "device info: {%s}", DeviceUtils.retrieveDeviceInfo(context));
        LogUtils.info(TAG, "fu sdk version %s", faceunity.fuGetVersion());
        faceunity.fuSetup(new byte[0], authpack.A());
        faceunity.fuReleaseEGLContext();
        boolean isInit = isLibInit();
        sIsInited = isInit;
        LogUtils.info(TAG, "fuSetup. isLibInit: %s", isInit);

        BundleUtils.loadAiModel(context, ASSETS_DIR_MODEL + "ai_face_processor.bundle", faceunity.FUAITYPE_FACEPROCESSOR);
        BundleUtils.loadAiModel(context, ASSETS_DIR_MODEL + "ai_human_processor.bundle", faceunity.FUAITYPE_HUMAN_PROCESSOR);
        BundleUtils.loadAiModel(context, ASSETS_DIR_MODEL + "ai_hand_processor.bundle", faceunity.FUAITYPE_HANDGESTURE);
        LogUtils.debug(TAG, "setup cost %dms", (int) (System.currentTimeMillis() - startTime));
    }

    public static void destroy() {
        if (sIsInited) {
            BundleUtils.releaseAiModel(faceunity.FUAITYPE_FACEPROCESSOR);
            BundleUtils.releaseAiModel(faceunity.FUAITYPE_HUMAN_PROCESSOR);
            BundleUtils.releaseAiModel(faceunity.FUAITYPE_HANDGESTURE);
            faceunity.fuDestroyLibData();
            boolean isInit = isLibInit();
            sIsInited = isInit;
            LogUtils.debug(TAG, "destroy. isLibInit: %s", isInit);
        }
    }

    public static boolean isLibInit() {
        return faceunity.fuIsLibraryInit() == 1;
    }


    public static int getModuleCode(int index) {
        return faceunity.fuGetModuleCode(index);
    }

    public static String getVersion() {
        return faceunity.fuGetVersion();
    }


    public static final class FuLogLevel {
        public static final int FU_LOG_LEVEL_TRACE = 0;
        public static final int FU_LOG_LEVEL_DEBUG = 1;
        public static final int FU_LOG_LEVEL_INFO = 2;
        public static final int FU_LOG_LEVEL_WARN = 3;
        public static final int FU_LOG_LEVEL_ERROR = 4;
        public static final int FU_LOG_LEVEL_CRITICAL = 5;
        public static final int FU_LOG_LEVEL_OFF = 6;
    }

    public static final class FuAiGestureType {
        public static final int FUAIGESTURE_UNKNOWN = 0;
        public static final int FUAIGESTURE_THUMB = 1;
        public static final int FUAIGESTURE_KORHEART = 2;
        public static final int FUAIGESTURE_SIX = 3;
        public static final int FUAIGESTURE_FIST = 4;
        public static final int FUAIGESTURE_PALM = 5;
        public static final int FUAIGESTURE_ONE = 6;
        public static final int FUAIGESTURE_TWO = 7;
        public static final int FUAIGESTURE_OK = 8;
        public static final int FUAIGESTURE_ROCK = 9;
        public static final int FUAIGESTURE_CROSS = 10;
        public static final int FUAIGESTURE_HOLD = 11;
        public static final int FUAIGESTURE_GREET = 12;
        public static final int FUAIGESTURE_PHOTO = 13;
        public static final int FUAIGESTURE_HEART = 14;
        public static final int FUAIGESTURE_MERGE = 15;
        public static final int FUAIGESTURE_EIGHT = 16;
        public static final int FUAIGESTURE_HALFFIST = 17;
        public static final int FUAIGESTURE_GUN = 18;
    }


    public static final class FuAiTongueType {
        public static final int FUAITONGUE_UNKNOWN = 0;
        public static final int FUAITONGUE_UP = 1 << 1;
        public static final int FUAITONGUE_DOWN = 1 << 2;
        public static final int FUAITONGUE_LEFT = 1 << 3;
        public static final int FUAITONGUE_RIGHT = 1 << 4;
        public static final int FUAITONGUE_LEFT_UP = 1 << 5;
        public static final int FUAITONGUE_LEFT_DOWN = 1 << 6;
        public static final int FUAITONGUE_RIGHT_UP = 1 << 7;
        public static final int FUAITONGUE_RIGHT_DOWN = 1 << 8;
        public static final int FUAITONGUE_FORWARD = 1 << 9;
        public static final int FUAITONGUE_NO_STRETCH = 1 << 10;
    }

    public static final class FuAiFaceExpressionType {
        public static final int FACE_EXPRESSION_UNKONW = 0;

        public static final int FACE_EXPRESSION_BROW_UP = 1 << 1;

        public static final int FACE_EXPRESSION_BROW_FROWN = 1 << 2;

        public static final int FACE_EXPRESSION_LEFT_EYE_CLOSE = 1 << 3;

        public static final int FACE_EXPRESSION_RIGHT_EYE_CLOSE = 1 << 4;

        public static final int FACE_EXPRESSION_EYE_WIDE = 1 << 5;

        public static final int FACE_EXPRESSION_MOUTH_SMILE_LEFT = 1 << 6;

        public static final int FACE_EXPRESSION_MOUTH_SMILE_RIGHT = 1 << 7;

        public static final int FACE_EXPRESSION_MOUTH_FUNNEL = 1 << 8;

        public static final int FACE_EXPRESSION_MOUTH_OPEN = 1 << 9;

        public static final int FACE_EXPRESSION_MOUTH_PUCKER = 1 << 10;

        public static final int FACE_EXPRESSION_MOUTH_ROLL = 1 << 11;

        public static final int FACE_EXPRESSION_MOUTH_PUFF = 1 << 12;

        public static final int FACE_EXPRESSION_MOUTH_SMILE = 1 << 13;

        public static final int FACE_EXPRESSION_MOUTH_FROWN = 1 << 14;

        public static final int FACE_EXPRESSION_HEAD_LEFT = 1 << 15;

        public static final int FACE_EXPRESSION_HEAD_RIGHT = 1 << 16;

        public static final int FACE_EXPRESSION_HEAD_NOD = 1 << 17;
    }


    public static final class FuAiEmotionType {
        public static final int FUAIEMOTION_UNKNOWN = 0;

        public static final int FUAIEMOTION_HAPPY = 1 << 1;

        public static final int FUAIEMOTION_SAD = 1 << 2;

        public static final int FUAIEMOTION_ANGRY = 1 << 3;

        public static final int FUAIEMOTION_SURPRISE = 1 << 4;

        public static final int FUAIEMOTION_FEAR = 1 << 5;

        public static final int FUAIEMOTION_DISGUST = 1 << 6;

        public static final int FUAIEMOTION_NEUTRAL = 1 << 7;

        public static final int FUAIEMOTION_CONFUSE = 1 << 8;
    }

    private mRenderer(Context context) {
        mContext = context;
    }


    public void onSurfaceCreated() {
        LogUtils.info(TAG, "onSurfaceCreated");
        mGlThreadId = Thread.currentThread().getId();
        HandlerThread handlerThread = new HandlerThread("FuNamaWorker");
        handlerThread.start();
        mFuItemHandler = new FUItemHandler(handlerThread.getLooper());
        mItemsArray = new int[1];

        if (mIsCreateEGLContext) {
            faceunity.fuCreateEGLContext();
        }
        mRotationMode = createRotationMode();

        faceunity.fuSetDefaultRotationMode(mRotationMode);

        faceunity.fuHumanProcessorSetMaxHumans(mMaxHumans);

        faceunity.fuSetMaxFaces(mMaxFaces);

        if (mRenderMode == RENDER_MODE_CONTROLLER) {
            mRenderMode = RENDER_MODE_NORMAL;
            loadController(mControllerCallback);
        } else {
            for (Effect effect : mEffectList) {
                Message.obtain(mFuItemHandler, FUItemHandler.MESSAGE_WHAT_LOAD_EFFECT, effect).sendToTarget();
            }
        }
    }

    /**

     * @param maxFaces
     */
    public void setMaxFaces(final int maxFaces) {
        mMaxFaces = maxFaces;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                faceunity.fuSetMaxFaces(maxFaces);
                LogUtils.info(TAG, "setMaxFaces: %s", maxFaces);
            }
        });
    }

    public void loadController(final Callback callback) {
        mControllerCallback = callback;
        if (mFuItemHandler != null) {
            Message.obtain(mFuItemHandler, FUItemHandler.MESSAGE_WHAT_LOAD_CONTROLLER, callback).sendToTarget();
        }
    }

    public void destroyController(final Callback callback) {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                destroyControllerRelated();
                for (int item : mItemsArray) {
                    if (item > 0) {
                        faceunity.fuDestroyItem(item);
                    }
                }
                Arrays.fill(mItemsArray, 0);
                faceunity.fuSetDefaultRotationMode(mRotationMode);
                mRenderMode = RENDER_MODE_NORMAL;
                resetTrackStatus();
                for (Effect effect : mEffectList) {
                    Message.obtain(mFuItemHandler, FUItemHandler.MESSAGE_WHAT_LOAD_EFFECT, effect).sendToTarget();
                }
                if (callback != null) {
                    callback.onSuccess();
                }
            }
        });
    }

    public void setHumanTrackScene(final int humanTrackScene) {
        LogUtils.debug(TAG, "setHumanTrackScene() humanTrackScene: %d", humanTrackScene);
        mHumanTrackScene = humanTrackScene;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                int item = mItemsArray[0];
                if (item > 0) {
                    boolean isFullScene = humanTrackScene == HUMAN_TRACK_SCENE_FULL;
                    if (isFullScene) {
                        faceunity.fuItemSetParam(item, "target_position", new double[]{0.0, 53.14, -537.94});
                        faceunity.fuItemSetParam(item, "target_angle", 0.0);
                        faceunity.fuItemSetParam(item, "reset_all", 3.0);
                    } else {
                        faceunity.fuItemSetParam(item, "target_position", new double[]{0.0, 11.76, -183.89});
                        faceunity.fuItemSetParam(item, "target_angle", 0);
                        faceunity.fuItemSetParam(item, "reset_all", 6);
                    }
                    faceunity.fuItemSetParam(item, "human_3d_track_set_scene", humanTrackScene);
                }
            }
        });
    }

    public void resetInputCameraMatrix() {
        faceunity.fuSetInputCameraBufferMatrixState(0);
        faceunity.fuSetInputCameraTextureMatrixState(0);
    }

    private int[] validateItems(int[] input) {
        int[] output = new int[input.length];
        int count = 0;
        for (int i : input) {
            if (i > 0) {
                output[count++] = i;
            }
        }
        return Arrays.copyOfRange(output, 0, count);
    }

    private void rotateImage(byte[] img, int width, int height) {
        boolean isFrontCam = mCameraFacing == CAMERA_FACING_FRONT;

        int rotateMode = isFrontCam ? faceunity.FU_ROTATION_MODE_270 : faceunity.FU_ROTATION_MODE_90;

        int flipX = isFrontCam ? 1 : 0;

        int flipY = 0;
        faceunity.fuRotateImage(mRotatedImage, img, faceunity.FU_FORMAT_NV21_BUFFER, width, height, rotateMode, flipX, flipY);
        faceunity.fuSetInputCameraMatrix(flipX, flipY, rotateMode);
    }

    public int drawFrame(byte[] img, int tex, int w, int h) {
        if (img == null || tex <= 0 || w <= 0 || h <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        int fuTex;
        boolean renderNormal = mRenderMode == RENDER_MODE_NORMAL;

        if (renderNormal) {
            //fuTex = faceunity.fuRenderImg(w, h, mFrameId++, mItemsArray, 0, img, faceunity.FU_FORMAT_NV21_BUFFER, 1, 1, new byte[]{0});
            faceunity.fuSetInputCameraTextureMatrixState(0);
            faceunity.fuSetInputCameraBufferMatrixState(0);
            int flags = createFlags();
            flags ^= mInputTextureType;
            fuTex = faceunity.fuRenderToNV21Image(img, w, h, mFrameId++, mItemsArray, flags);
            GLES30.glFinish();
        } else {
            rotateImage(img, w, h);
            fuTex = faceunity.fuRenderBundlesWithCamera(mRotatedImage.mData, tex, faceunity.FU_ADM_FLAG_EXTERNAL_OES_TEXTURE,
                    mRotatedImage.mWidth, mRotatedImage.mHeight, mFrameId++, mItemsArray);
        }
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     * trackHumanAction
     *
     * @param img
     * @param width
     * @param height
     */
    public static void trackHumanAction(byte[] img, int width, int height, int format) {
        if (img == null || width <= 0 || height <= 0) {
            LogUtils.error(TAG, "trackHumanAction data is invalid");
            return;
        }
        track(img, width, height, faceunity.FUAITYPE_HUMAN_PROCESSOR_2D_DANCE, format);
    }

    /**
     * trackHumanGesture
     *
     * @param img
     * @param width
     * @param height
     */
    public static void trackHumanGesture(byte[] img, int width, int height, int format) {
        if (img == null || width <= 0 || height <= 0) {
            LogUtils.error(TAG, "trackHumanGesture data is invalid");
            return;
        }
        track(img, width, height, faceunity.FUAITYPE_HANDGESTURE, format);
    }

    /**
     * Human Face Gesture
     *
     * @param img
     * @param width
     * @param height
     * @param aiType
     * @param format
     */
    public static void track(byte[] img, int width, int height, int aiType, int format) {
        faceunity.fuSetTrackFaceAIType(aiType);
        faceunity.fuTrackFace(img, format, width, height);
    }

    /**
     * Gesture
     *
     * @return
     */
    public static int detectHumanGesture() {
        if (faceunity.fuHandDetectorGetResultNumHands() > 0) {
            return faceunity.fuHandDetectorGetResultGestureType(0);
        }
        return -1;
    }

    /**
     * Detect pose type
     *
     * @return
     */
    public static int detectHumanAction() {
        if (faceunity.fuHumanProcessorGetNumResults() > 0) {
            return faceunity.fuHumanProcessorGetResultActionType(0);
        }
        return -1;
    }

    /**
     * Tongue
     *
     * @return
     */
    public int detectFaceTongue() {
        if (faceunity.fuIsTracking() > 0) {
            int[] tongueDirection = mTongueDirection;
            faceunity.fuGetFaceInfo(0, "tongue_direction", tongueDirection);
            return tongueDirection[0];
        }
        return -1;
    }

    /**
     * Emotion
     *
     * @param
     */
    public void detectFaceEmotion(int[] result) {
        Arrays.fill(result, 0);
        if (faceunity.fuIsTracking() > 0) {
            int[] emotion = mEmotion;
            faceunity.fuGetFaceInfo(0, "emotion", emotion);
            int em = emotion[0];
            if ((em & FuAiEmotionType.FUAIEMOTION_HAPPY) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_HAPPY;
            } else if ((em & FuAiEmotionType.FUAIEMOTION_SAD) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_SAD;
            } else if ((em & FuAiEmotionType.FUAIEMOTION_ANGRY) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_ANGRY;
            } else if ((em & FuAiEmotionType.FUAIEMOTION_SURPRISE) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_SURPRISE;
            } else if ((em & FuAiEmotionType.FUAIEMOTION_FEAR) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_FEAR;
            } else if ((em & FuAiEmotionType.FUAIEMOTION_DISGUST) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_DISGUST;
            } else if ((em & FuAiEmotionType.FUAIEMOTION_NEUTRAL) != 0) {
                result[0] = FuAiEmotionType.FUAIEMOTION_NEUTRAL;
            }
            if ((em & FuAiEmotionType.FUAIEMOTION_CONFUSE) != 0) {
                result[1] = 1;
            }
//            LogUtils.debug(TAG, "emotion %s, result %s ", Arrays.toString(emotion), Arrays.toString(result));
        }
    }

    /**
     * detectFaceExpression
     *
     * @param result
     * @return
     */
    public void detectFaceExpression(int[] result) {
        if (result == null || result.length < 4) {
            return;
        }
        Arrays.fill(result, 0);
        if (faceunity.fuIsTracking() > 0) {
            int[] expressionType = mExpressionType;
            faceunity.fuGetFaceInfo(0, "expression_type", expressionType);
            int et = expressionType[0];
            if (et > 0) {
                int index = 0;
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_BROW_UP) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_BROW_UP;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_BROW_FROWN) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_BROW_FROWN;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_LEFT_EYE_CLOSE) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_LEFT_EYE_CLOSE;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_RIGHT_EYE_CLOSE) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_RIGHT_EYE_CLOSE;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_EYE_WIDE) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_EYE_WIDE;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE_LEFT) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE_LEFT;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE_RIGHT) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE_RIGHT;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE_RIGHT) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE_RIGHT;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_FUNNEL) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_FUNNEL;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_OPEN) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_OPEN;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_PUCKER) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_PUCKER;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_ROLL) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_ROLL;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_PUFF) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_PUFF;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_SMILE;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_FROWN) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_MOUTH_FROWN;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_HEAD_LEFT) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_HEAD_LEFT;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_HEAD_RIGHT) != 0) {
                    result[index++] = FuAiFaceExpressionType.FACE_EXPRESSION_HEAD_RIGHT;
                }
                if ((et & FuAiFaceExpressionType.FACE_EXPRESSION_HEAD_NOD) != 0) {
                    result[index] = FuAiFaceExpressionType.FACE_EXPRESSION_HEAD_NOD;
                }
            }
        }
    }

    /**
     * getFaceRotationEuler pitch yaw roll
     *
     * @return
     */
    public float[] getFaceRotationEuler() {
        float[] rotationEuler = mRotationEuler;
        if (faceunity.fuIsTracking() > 0) {
            faceunity.fuGetFaceInfo(0, "rotation_euler", rotationEuler);
            for (int i = 0; i < rotationEuler.length; i++) {
                rotationEuler[i] = (float) (rotationEuler[i] * 180 / Math.PI);
            }
        } else {
            Arrays.fill(rotationEuler, 0F);
        }
        return rotationEuler;
    }

    /**
     * 识别到的人脸数
     *
     * @return
     */
    public int queryFaceTrackStatus() {
        return faceunity.fuIsTracking();
    }

    /**
     * face numbers
     *
     * @return
     */
    public int queryHumanTrackStatus() {
        return faceunity.fuHumanProcessorGetNumResults();
    }

    /**
     *  texture interface
     *
     * @param tex
     * @param w
     * @param h
     * @return
     */
    public int onDrawFrameSingleInput(int tex, int w, int h) {
        if (tex <= 0 || w <= 0 || h <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        int flags = createFlags();
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        int fuTex = faceunity.fuRenderToTexture(tex, w, h, mFrameId++, mItemsArray, flags);
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     *  texture interface with callback
     *
     * @param tex
     * @param w
     * @param h
     * @param readBackImg
     * @param readBackW
     * @param readBackH
     * @param readBackFormat buffer : nv21, i420, rgba
     * @return
     */
    public int onDrawFrameSingleInput(int tex, int w, int h, byte[] readBackImg, int readBackW, int readBackH, int readBackFormat) {
        if (tex <= 0 || w <= 0 || h <= 0 || readBackImg == null || readBackW <= 0 || readBackH <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        int flags = createFlags();
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        switch (readBackFormat) {
            case INPUT_FORMAT_I420_BUFFER:
                flags |= faceunity.FU_ADM_FLAG_I420_TEXTURE;
                break;
            case INPUT_FORMAT_RGBA_BUFFER:
                flags |= faceunity.FU_ADM_FLAG_RGBA_BUFFER;
                break;
            case INPUT_FORMAT_NV21_BUFFER:
            default:
                flags |= faceunity.FU_ADM_FLAG_NV21_TEXTURE;
                break;
        }

        int fuTex = faceunity.fuRenderToTexture(tex, w, h, mFrameId++, mItemsArray, flags, readBackImg, readBackW, readBackH);
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     *
     * @param img
     * @param w
     * @param h
     * @param format buffer : nv21, i420, rgba
     * @return
     */
    public int onDrawFrameSingleInput(byte[] img, int w, int h, int format) {
        if (img == null || w <= 0 || h <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        int flags = createFlags();
        flags ^= mInputTextureType;
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        int fuTex;
        switch (format) {
            case INPUT_FORMAT_I420_BUFFER:
                fuTex = faceunity.fuRenderToI420Image(img, w, h, mFrameId++, mItemsArray, flags);
                break;
            case INPUT_FORMAT_RGBA_BUFFER:
                fuTex = faceunity.fuRenderToRgbaImage(img, w, h, mFrameId++, mItemsArray, flags);
                break;
            case INPUT_FORMAT_NV21_BUFFER:
            default:
                fuTex = faceunity.fuRenderToNV21Image(img, w, h, mFrameId++, mItemsArray, flags);
                break;
        }
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     *
     * @param img
     * @param w
     * @param h
     * @param readBackImg  buffer
     * @param readBackW
     * @param readBackH
     * @param format      buffer : nv21, i420, rgba
     * @return
     */
    public int onDrawFrameSingleInput(byte[] img, int w, int h, byte[] readBackImg, int readBackW, int readBackH, int format) {
        if (img == null || w <= 0 || h <= 0 || readBackImg == null || readBackW <= 0 || readBackH <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        int flags = createFlags();
        flags ^= mInputTextureType;
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        int fuTex;
        switch (format) {
            case INPUT_FORMAT_I420_BUFFER:
                fuTex = faceunity.fuRenderToI420Image(img, w, h, mFrameId++, mItemsArray, flags,
                        readBackW, readBackH, readBackImg);
                break;
            case INPUT_FORMAT_RGBA_BUFFER:
                fuTex = faceunity.fuRenderToRgbaImage(img, w, h, mFrameId++, mItemsArray, flags,
                        readBackW, readBackH, readBackImg);
                break;
            case INPUT_FORMAT_NV21_BUFFER:
            default:
                fuTex = faceunity.fuRenderToNV21Image(img, w, h, mFrameId++, mItemsArray, flags,
                        readBackW, readBackH, readBackImg);
                break;
        }
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     *
     * @param img NV21
     * @param tex  ID
     * @param w
     * @param h
     * @return
     */
    public int onDrawFrameDualInput(byte[] img, int tex, int w, int h) {
        if (img == null || tex <= 0 || w <= 0 || h <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        int flags = createFlags();
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        int fuTex = faceunity.fuDualInputToTexture(img, tex, flags, w, h, mFrameId++, mItemsArray);
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     *
     * @param img         NV21
     * @param tex         ID
     * @param w
     * @param h
     * @param readBackImg
     * @param readBackW
     * @param readBackH
     * @return
     */
    public int onDrawFrameDualInput(byte[] img, int tex, int w, int h, byte[] readBackImg, int readBackW, int readBackH) {
        if (img == null || tex <= 0 || w <= 0 || h <= 0 || readBackImg == null || readBackW <= 0 || readBackH <= 0) {
            LogUtils.error(TAG, "onDrawFrame data is invalid");
            return 0;
        }
        prepareDrawFrame();
        int flags = createFlags();
        if (mIsRunBenchmark) {
            mCallStartTime = System.nanoTime();
        }
        int fuTex = faceunity.fuDualInputToTexture(img, tex, flags, w, h, mFrameId++, mItemsArray,
                readBackW, readBackH, readBackImg);
        if (mIsRunBenchmark) {
            mSumRenderTime += System.nanoTime() - mCallStartTime;
        }
        return fuTex;
    }

    /**
     *
     */
    public void onSurfaceDestroyed() {
        LogUtils.info(TAG, "onSurfaceDestroyed");
        if (mFuItemHandler != null) {
            mFuItemHandler.removeCallbacksAndMessages(null);
            mFuItemHandler.getLooper().quit();
            mFuItemHandler = null;
        }
        mFrameId = 0;
        mGlThreadId = 0;
        synchronized (this) {
            mEventQueue.clear();
        }

        destroyControllerRelated();
        if (mItemsArray != null) {
            for (int item : mItemsArray) {
                if (item > 0) {
                    faceunity.fuDestroyItem(item);
                }
            }
            Arrays.fill(mItemsArray, 0);
            for (Effect effect : mEffectList) {
                effect.setHandle(0);
            }
            mItemsArray = null;
        }
        faceunity.fuOnCameraChange();
        faceunity.fuHumanProcessorReset();
        faceunity.fuDone();
        faceunity.fuOnDeviceLost();
        if (mIsCreateEGLContext) {
            faceunity.fuReleaseEGLContext();
        }
    }

    private void destroyControllerRelated() {
        if (mControllerBoundItems != null && mControllerBoundItems[0] > 0) {
            int controllerItem = mItemsArray[0];
            faceunity.fuItemSetParam(controllerItem, "enable_human_processor", 0.0);
            int[] controllerBoundItems = validateItems(mControllerBoundItems);
            LogUtils.debug(TAG, "destroyControllerRelated: unbind %s", Arrays.toString(controllerBoundItems));
            faceunity.fuUnBindItems(controllerItem, controllerBoundItems);
            for (int i = controllerBoundItems.length - 1; i >= 0; i--) {
                int ptaItem = controllerBoundItems[i];
                if (ptaItem > 0) {
                    faceunity.fuDestroyItem(ptaItem);
                }
            }
            Arrays.fill(controllerBoundItems, 0);
            mControllerBoundItems = null;
        }
    }


    private void prepareDrawFrame() {

        benchmarkFPS();
//        callTrackStatus();

        int errorCode = faceunity.fuGetSystemError();
        if (errorCode != 0) {
            String errorMessage = faceunity.fuGetSystemErrorString(errorCode);
            LogUtils.error(TAG, "system error code: %d, error message: %s", errorCode, errorMessage);
        }

        synchronized (this) {
            while (!mEventQueue.isEmpty()) {
                mEventQueue.remove(0).run();
            }
        }
    }

    private void callTrackStatus() {

        int trackFace = faceunity.fuIsTracking();

        int trackHumans = faceunity.fuHumanProcessorGetNumResults();

//        int trackGesture = faceunity.fuHandDetectorGetResultNumHands();
        if (mOnTrackStatusChangedListener != null) {
//            if (mTrackGestureStatus != trackGesture) {
//                mTrackGestureStatus = trackGesture;
//                mOnTrackStatusChangedListener.onTrackStatusChanged(TRACK_TYPE_GESTURE, trackGesture);
//            }
            if (mTrackHumanStatus != trackHumans) {
                mTrackHumanStatus = trackHumans;
                mOnTrackStatusChangedListener.onTrackStatusChanged(TRACK_TYPE_HUMAN, trackHumans);
            }
            if (mTrackFaceStatus != trackFace) {
                mTrackFaceStatus = trackFace;
                mOnTrackStatusChangedListener.onTrackStatusChanged(TRACK_TYPE_FACE, trackFace);
            }
        }
    }

    /**
     *
     * @param r
     */
    public void queueEvent(Runnable r) {
        if (r == null) {
            return;
        }
        if (mGlThreadId == Thread.currentThread().getId()) {
            r.run();
        } else {
            synchronized (this) {
                mEventQueue.add(r);
            }
        }
    }

    /**
     *
     * @param deviceOrientation
     */
    public void onDeviceOrientationChanged(final int deviceOrientation) {
        if (mDeviceOrientation == deviceOrientation) {
            return;
        }
        LogUtils.debug(TAG, "onDeviceOrientationChanged() deviceOrientation: %d", deviceOrientation);
        mDeviceOrientation = deviceOrientation;
        callWhenDeviceChanged();
    }

    /**
     * onCameraChanged
     *
     * @param cameraFacing
     * @param cameraOrientation
     */
    public void onCameraChanged(final int cameraFacing, final int cameraOrientation) {
        if (mCameraFacing == cameraFacing && mInputImageOrientation == cameraOrientation) {
            return;
        }
        LogUtils.debug(TAG, "onCameraChanged() cameraFacing: %d, cameraOrientation: %d", cameraFacing, cameraOrientation);
        mCameraFacing = cameraFacing;
        mInputImageOrientation = cameraOrientation;
        queueEvent(new Runnable() {
            @Override
            public void run() {
                faceunity.fuHumanProcessorReset();
                faceunity.fuOnCameraChange();
            }
        });
        callWhenDeviceChanged();
    }

    private void callWhenDeviceChanged() {
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mRotationMode = createRotationMode();
                int rotationMode = 0;
                if (mRenderMode == RENDER_MODE_NORMAL) {
                    rotationMode = mRotationMode;
                }
                faceunity.fuSetDefaultRotationMode(rotationMode);
            }
        });
    }

    private int createRotationMode() {
        if (mInputTextureType == mRenderer.INPUT_TEXTURE_2D) {
            return faceunity.FU_ROTATION_MODE_0;
        }
        int rotMode = faceunity.FU_ROTATION_MODE_0;
        if (mInputImageOrientation == 270) {
            if (mCameraFacing == CAMERA_FACING_FRONT) {
                rotMode = mDeviceOrientation / 90;
            } else {
                if (mDeviceOrientation == 90) {
                    rotMode = faceunity.FU_ROTATION_MODE_270;
                } else if (mDeviceOrientation == 270) {
                    rotMode = faceunity.FU_ROTATION_MODE_90;
                } else {
                    rotMode = mDeviceOrientation / 90;
                }
            }
        } else if (mInputImageOrientation == 90) {
            if (mCameraFacing == CAMERA_FACING_BACK) {
                if (mDeviceOrientation == 90) {
                    rotMode = faceunity.FU_ROTATION_MODE_270;
                } else if (mDeviceOrientation == 270) {
                    rotMode = faceunity.FU_ROTATION_MODE_90;
                } else {
                    rotMode = mDeviceOrientation / 90;
                }
            } else {
                if (mDeviceOrientation == 0) {
                    rotMode = faceunity.FU_ROTATION_MODE_180;
                } else if (mDeviceOrientation == 90) {
                    rotMode = faceunity.FU_ROTATION_MODE_270;
                } else if (mDeviceOrientation == 180) {
                    rotMode = faceunity.FU_ROTATION_MODE_0;
                } else {
                    rotMode = faceunity.FU_ROTATION_MODE_90;
                }
            }
        }
        return rotMode;
    }

    private int createFlags() {
        int flags = mInputTextureType | mInputImageFormat;
        if (mInputTextureType == INPUT_TEXTURE_2D || mCameraFacing != CAMERA_FACING_FRONT) {
            flags |= faceunity.FU_ADM_FLAG_FLIP_X;
        }
        return flags;
    }

    public void selectEffect(final Effect effect) {
        if (effect == null) {
            return;
        }
        if (!mEffectList.add(effect)) {
            return;
        }
        LogUtils.info(TAG, "selectEffect: %s", effect);
        if (mFuItemHandler != null) {
            Message.obtain(mFuItemHandler, FUItemHandler.MESSAGE_WHAT_LOAD_EFFECT, effect).sendToTarget();
        }
    }

    public void unselectEffect(final Effect effect) {
        if (effect == null) {
            return;
        }
        if (!mEffectList.remove(effect)) {
            return;
        }
        final int handle = effect.getHandle();
        if (handle <= 0) {
            return;
        }
        LogUtils.info(TAG, "unselectEffect: %s", effect);
        queueEvent(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mItemsArray.length; i++) {
                    if (mItemsArray[i] == handle) {
                        faceunity.fuDestroyItem(handle);
                        mItemsArray[i] = 0;
                        LogUtils.debug(TAG, "destroy item handle: %d", handle);
                        break;
                    }
                }
                effect.setHandle(0);
                int newLength = 0;
                for (int value : mItemsArray) {
                    if (value > 0) {
                        newLength++;
                    }
                }
                if (newLength == 0) {
                    newLength = 1;
                }
                int[] trimmedItems = new int[newLength];
                for (int i = 0, j = 0; i < mItemsArray.length; i++) {
                    if (mItemsArray[i] > 0) {
                        trimmedItems[j++] = mItemsArray[i];
                    }
                }
                mItemsArray = trimmedItems;
                // always set max face 8
                faceunity.fuSetMaxFaces(mMaxFaces);
                resetTrackStatus();
                LogUtils.debug(TAG, "new item array: %s", Arrays.toString(trimmedItems));
            }
        });
    }

    public void setEffectItemParam(final int handle, final String key, final Object value) {
        if (handle <= 0 || key == null || value == null) {
            return;
        }
        queueEvent(new Runnable() {
            @Override
            public void run() {
                setItemParam(handle, key, value);
            }
        });
    }

    public void resetTrackStatus() {
        LogUtils.debug(TAG, "resetTrackStatus: ");
        queueEvent(new Runnable() {
            @Override
            public void run() {
                mTrackFaceStatus = -1;
                mTrackHumanStatus = -1;
                mTrackGestureStatus = -1;
            }
        });
    }

    private void setItemParam(int handle, String key, Object value) {
        LogUtils.debug(TAG, "fuItemSetParam. handle: %d, key: %s, value: %s", handle, key, value);
        if (value instanceof Double) {
            faceunity.fuItemSetParam(handle, key, (Double) value);
        } else if (value instanceof Integer) {
            faceunity.fuItemSetParam(handle, key, (Integer) value);
        } else if (value instanceof Float) {
            faceunity.fuItemSetParam(handle, key, (Float) value);
        } else if (value instanceof String) {
            faceunity.fuItemSetParam(handle, key, (String) value);
        } else if (value instanceof double[]) {
            faceunity.fuItemSetParam(handle, key, (double[]) value);
        }
    }

    //-----------------------------Detection-----------------------------------

    private int mTrackHumanStatus = -1;
    private int mTrackFaceStatus = -1;
    private int mTrackGestureStatus = -1;

    public interface OnTrackStatusChangedListener {
        /**
         *
         * @param type
         * @param status
         */
        void onTrackStatusChanged(int type, int status);
    }

    private OnTrackStatusChangedListener mOnTrackStatusChangedListener;

    //-------------------------Error Callback---------------------------------

    public interface OnSystemErrorListener {
        /**
         *
         * @param effect
         */
        void onSystemError(Effect effect);
    }

    private OnSystemErrorListener mOnSystemErrorListener;

    //------------------------------FPS render callback------------------------------------

    private static final int NANO_IN_ONE_MILLI_SECOND = 1_000_000;
    private static final int NANO_IN_ONE_SECOND = 1_000_000_000;
    private static final int FRAME_COUNT = 10;
    private boolean mIsRunBenchmark = false;
    private int mCurrentFrameCount;
    private long mLastFrameTimestamp;
    private long mSumRenderTime;
    private long mCallStartTime;
    private OnDebugListener mOnDebugListener;

    public interface OnDebugListener {
        /**
         *  10 data frame average
         *
         * @param fps         FPS
         * @param renderTime
         * @param elapsedTime
         */
        void onFpsChanged(double fps, double renderTime, double elapsedTime);
    }

    private void benchmarkFPS() {
        if (!mIsRunBenchmark) {
            return;
        }
        if (++mCurrentFrameCount == FRAME_COUNT) {
            long tmp = System.nanoTime();
            double elapsedTime = (double) (tmp - mLastFrameTimestamp) / FRAME_COUNT;
            double fps = (double) NANO_IN_ONE_SECOND / elapsedTime;
            double renderTime = (double) mSumRenderTime / FRAME_COUNT / NANO_IN_ONE_MILLI_SECOND;
            mLastFrameTimestamp = tmp;
            mSumRenderTime = 0;
            mCurrentFrameCount = 0;

            if (mOnDebugListener != null) {
                mOnDebugListener.onFpsChanged(fps, renderTime, elapsedTime / NANO_IN_ONE_MILLI_SECOND);
            }
        }
    }

    private static boolean isEffectModuleEnable(Effect effect) {
        String authCode = effect.getAuthCode();
        if (authCode != null) {
            String[] codeStr = authCode.split("-");
            if (codeStr.length == 2) {
                int moduleCode0 = mRenderer.getModuleCode(0);
                int moduleCode1 = mRenderer.getModuleCode(1);
                int code0 = Integer.parseInt(codeStr[0]);
                int code1 = Integer.parseInt(codeStr[1]);
                return (moduleCode0 == 0 && moduleCode1 == 0) || ((code0 & moduleCode0) > 0 || (code1 & moduleCode1) > 0);
            }
        }
        return false;
    }

    //--------------------------------------IO handler async thtread-------------------------------------

    private class FUItemHandler extends Handler {
        private static final int MESSAGE_WHAT_LOAD_EFFECT = 666;
        private static final int MESSAGE_WHAT_LOAD_CONTROLLER = 888;

        FUItemHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == MESSAGE_WHAT_LOAD_EFFECT) {
                final Effect effect = (Effect) msg.obj;
                if (effect == null) {
                    return;
                }
                final int itemEffect = BundleUtils.loadItem(mContext, effect.getFilePath());
                effect.setHandle(itemEffect);
                boolean enable = isEffectModuleEnable(effect);
                if (!enable) {
                    if (mOnSystemErrorListener != null) {
                        mOnSystemErrorListener.onSystemError(effect);
                    }
                    return;
                }
                if (itemEffect <= 0) {
                    LogUtils.warn(TAG, "load item failed: %d", itemEffect);
                    return;
                }
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        Map<String, Object> params = effect.getParams();
                        if (params != null && params.size() > 0) {
                            Set<Map.Entry<String, Object>> entries = params.entrySet();
                            for (Map.Entry<String, Object> entry : entries) {
                                String key = entry.getKey();
                                Object value = entry.getValue();
                                if (key != null && value != null) {
                                    setItemParam(itemEffect, key, value);
                                }
                            }
                        }

                        if (mItemsArray[0] > 0) {
                            int newLength = 0;
                            for (int value : mItemsArray) {
                                if (value > 0) {
                                    newLength++;
                                }
                            }
                            int[] enlargedItems = new int[newLength + 1];
                            int length = mItemsArray.length;
                            for (int i = 0, j = 0; i < length; i++) {
                                if (mItemsArray[i] > 0) {
                                    enlargedItems[j++] = mItemsArray[i];
                                }
                            }
                            enlargedItems[newLength] = itemEffect;
                            mItemsArray = enlargedItems;
                        } else {
                            mItemsArray[0] = itemEffect;
                        }
                        // always set max face 8
                        faceunity.fuSetMaxFaces(mMaxFaces);
                        resetTrackStatus();
                        LogUtils.debug(TAG, "new item array: %s", Arrays.toString(mItemsArray));
                    }
                });
            } else if (msg.what == MESSAGE_WHAT_LOAD_CONTROLLER) {
                final mRenderer.Callback callback = (mRenderer.Callback) msg.obj;
                int[] defaultItems = new int[3];
                final int controllerItem = BundleUtils.loadItem(mContext, ASSETS_DIR_GRAPHICS + "controller.bundle");
                if (controllerItem <= 0) {
                    if (callback != null) {
                        callback.onFailure();
                    }
                    return;
                }
                final int controllerConfigItem = BundleUtils.loadItem(mContext, ASSETS_DIR_PTA + "controller_config.bundle");
                defaultItems[0] = controllerConfigItem;
                if (controllerConfigItem > 0) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            faceunity.fuBindItems(controllerItem, new int[]{controllerConfigItem});
                            LogUtils.debug(TAG, "run: controller bind config");
                        }
                    });
                }
                final int bgItem = BundleUtils.loadItem(mContext, ASSETS_DIR_PTA + "default_bg.bundle");
                defaultItems[1] = bgItem;
                if (bgItem > 0) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            faceunity.fuBindItems(controllerItem, new int[]{bgItem});
                            LogUtils.debug(TAG, "run: controller bind default bg");
                        }
                    });
                }
                final int fakeManItem = BundleUtils.loadItem(mContext, ASSETS_DIR_PTA + "fakeman.bundle");
                defaultItems[2] = fakeManItem;
                if (fakeManItem > 0) {
                    queueEvent(new Runnable() {
                        @Override
                        public void run() {
                            faceunity.fuBindItems(controllerItem, new int[]{fakeManItem});
                            LogUtils.debug(TAG, "run: controller bind fake man");
                        }
                    });
                }
                final int fxaaItem = BundleUtils.loadItem(mContext, ASSETS_DIR_GRAPHICS + "fxaa.bundle");
                int[] gestureItems = new int[GESTURE_BIND_BUNDLES.length];
                for (int i = 0; i < GESTURE_BIND_BUNDLES.length; i++) {
                    int item = BundleUtils.loadItem(mContext, ASSETS_DIR_PTA + "gesture/" + GESTURE_BIND_BUNDLES[i]);
                    gestureItems[i] = item;
                }
                final int[] validGestureItems = validateItems(gestureItems);
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        faceunity.fuBindItems(controllerItem, validGestureItems);
                        LogUtils.debug(TAG, "run: controller bind gesture");
                    }
                });
                int[] validDefaultItems = validateItems(defaultItems);
                int[] controllerBoundItems = new int[validDefaultItems.length + validGestureItems.length];
                System.arraycopy(validDefaultItems, 0, controllerBoundItems, 0, validDefaultItems.length);
                System.arraycopy(validGestureItems, 0, controllerBoundItems, validDefaultItems.length, validGestureItems.length);
                mControllerBoundItems = controllerBoundItems;
                LogUtils.debug(TAG, "run: controller all bind item %s", Arrays.toString(controllerBoundItems));
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        for (int item : mItemsArray) {
                            if (item > 0) {
                                faceunity.fuDestroyItem(item);
                            }
                        }
                        for (Effect effect : mEffectList) {
                            effect.setHandle(0);
                        }
                        Arrays.fill(mItemsArray, 0);

                        faceunity.fuItemSetParam(controllerItem, "close_face_capture", 1.0);

                        faceunity.fuItemSetParam(controllerItem, "is_close_dde", 1.0);

                        faceunity.fuItemSetParam(controllerItem, "enable_human_processor", 1.0);
                        int[] items = new int[2];
                        items[0] = controllerItem;
                        items[1] = fxaaItem;
                        mItemsArray = items;
                        setHumanTrackScene(mHumanTrackScene);
                        mRotationMode = faceunity.fuGetCurrentRotationMode();
                        faceunity.fuSetDefaultRotationMode(0);
                        mRenderMode = RENDER_MODE_CONTROLLER;
                        resetTrackStatus();

                        if (callback != null) {
                            callback.onSuccess();
                        }
                    }
                });
            }
        }

    }

    //--------------------------------------Builder----------------------------------------

    /**
     * mRenderer Builder
     */
    public static class Builder {
        private Context context;
        private boolean isCreateEGLContext;
        private int maxHumans = 1;
        private int maxFaces = MAX_FACE_COUNT;
        private int deviceOrientation = 90;
        private int inputTextureType = INPUT_TEXTURE_2D;
        private int inputImageFormat = 0;
        private int inputImageOrientation = 270;
        private int cameraFacing = CAMERA_FACING_FRONT;
        private boolean isRunBenchmark;
        private OnDebugListener onDebugListener;
        private OnTrackStatusChangedListener onTrackStatusChangedListener;
        private OnSystemErrorListener onSystemErrorListener;

        public Builder(Context context) {
            this.context = context.getApplicationContext();
        }

        /**
         * @param isCreateEGLContext
         * @return
         */

        public Builder setCreateEGLContext(boolean isCreateEGLContext) {
            this.isCreateEGLContext = isCreateEGLContext;
            return this;
        }

        /**
         * maxFaces
         *
         * @param maxFaces
         * @return
         */
        public Builder setFaces(int maxFaces) {
            this.maxFaces = maxFaces;
            return this;
        }

        /**
         * maxHumans
         *
         * @param maxHumans
         * @return
         */
        public Builder setHumans(int maxHumans) {
            this.maxHumans = maxHumans;
            return this;
        }

        /**
         * deviceOrientation
         *
         * @param deviceOrientation
         * @return
         */
        public Builder setDeviceOrientation(int deviceOrientation) {
            this.deviceOrientation = deviceOrientation;
            return this;
        }

        /**
         * inputTextureType
         *
         * @param inputTextureType OES or 2D
         * @return
         */
        public Builder setInputTextureType(int inputTextureType) {
            this.inputTextureType = inputTextureType;
            return this;
        }

        /**
         * inputImageFormat
         *
         * @param inputImageFormat
         * @return
         */
        public Builder setInputImageFormat(int inputImageFormat) {
            this.inputImageFormat = inputImageFormat;
            return this;
        }

        /**
         * inputImageOrientation
         *
         * @param inputImageOrientation
         * @return
         */
        public Builder setInputImageOrientation(int inputImageOrientation) {
            this.inputImageOrientation = inputImageOrientation;
            return this;
        }

        /**
         * cameraFacing
         *
         * @param cameraFacing
         * @return
         */
        public Builder setCameraFacing(int cameraFacing) {
            this.cameraFacing = cameraFacing;
            return this;
        }

        /**
         *  benchmark data
         *
         * @param isRunBenchmark
         * @return
         */
        public Builder setRunBenchmark(boolean isRunBenchmark) {
            this.isRunBenchmark = isRunBenchmark;
            return this;
        }

        /**
         * FPS callback
         *
         * @param onDebugListener
         * @return
         */
        public Builder setOnDebugListener(OnDebugListener onDebugListener) {
            this.onDebugListener = onDebugListener;
            return this;
        }

        /**
         * FacerackStatusChangedListener
         *
         * @param onTrackStatusChangedListener
         * @return
         */
        public Builder setOnTrackStatusChangedListener(OnTrackStatusChangedListener onTrackStatusChangedListener) {
            this.onTrackStatusChangedListener = onTrackStatusChangedListener;
            return this;
        }

        /**
         * SDK Error callback
         *
         * @param onSystemErrorListener
         * @return
         */
        public Builder setOnSystemErrorListener(OnSystemErrorListener onSystemErrorListener) {
            this.onSystemErrorListener = onSystemErrorListener;
            return this;
        }

        public mRenderer build() {
            mRenderer mRenderer = new mRenderer(context);
            mRenderer.mIsCreateEGLContext = isCreateEGLContext;
            mRenderer.mMaxHumans = maxHumans;
            mRenderer.mMaxFaces = maxFaces;
            mRenderer.mDeviceOrientation = deviceOrientation;
            mRenderer.mInputTextureType = inputTextureType;
            mRenderer.mInputImageFormat = inputImageFormat;
            mRenderer.mInputImageOrientation = inputImageOrientation;
            mRenderer.mCameraFacing = cameraFacing;
            mRenderer.mIsRunBenchmark = isRunBenchmark;
            mRenderer.mOnDebugListener = onDebugListener;
            mRenderer.mOnTrackStatusChangedListener = onTrackStatusChangedListener;
            mRenderer.mOnSystemErrorListener = onSystemErrorListener;

            LogUtils.debug(TAG, "mRenderer fields. isCreateEGLContext: " + isCreateEGLContext + ", maxFaces: "
                    + maxFaces + ", maxHumans: " + maxHumans + ", inputTextureType: " + inputTextureType
                    + ", inputImageFormat: " + inputImageFormat + ", inputImageOrientation: " + inputImageOrientation
                    + ", deviceOrientation: " + deviceOrientation + ", cameraFacing: "
                    + (cameraFacing == mRenderer.CAMERA_FACING_FRONT ? "front" : "back") + ", isRunBenchmark: " + isRunBenchmark);
            return mRenderer;
        }
    }

    public interface Callback {

        void onSuccess();

        void onFailure();
    }
}
