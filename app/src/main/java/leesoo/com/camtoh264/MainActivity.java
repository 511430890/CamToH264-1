package leesoo.com.camtoh264;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;


public class MainActivity extends AppCompatActivity {
    private TextureView camview,h264decodecview;
   private Camera mCamera;
    private int mPreviewWidth;
    private int mPreviewHeight;

    //引用了自己创建的编码解码2个类
   private VideoDecoder mVideoDecoder;
   private VideoEncoder mVideoEncoder;

    //This video stream format must be I420
   // private final static ArrayBlockingQueue<byte []> mInputDatasQueue = new ArrayBlockingQueue<byte []>(CACHE_BUFFER_SIZE);
    //Cachhe video stream which has been encoded.
    //private final static ArrayBlockingQueue<byte []> mOutputDatasQueue = new ArrayBlockingQueue<byte[]>(CACHE_BUFFER_SIZE);


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

    }
    private void initView() {
        camview = (TextureView) findViewById(R.id.camView);
        h264decodecview = (TextureView) findViewById(R.id.h264decodecView);
        camview.setSurfaceTextureListener(camviewTL);
        h264decodecview.setSurfaceTextureListener(h264decodecviewTL);
    }

    private TextureView.SurfaceTextureListener camviewTL = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
                openCamera(surfaceTexture,i,i1);
            //看图预览过程中创建对象，并启动编码器
            mVideoEncoder = new VideoEncoder("video/avc", mPreviewWidth, mPreviewHeight);
            mVideoEncoder.startEncoder();

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            mCamera.stopPreview();
            mCamera.release();
            if(mVideoEncoder != null){
                mVideoEncoder.release();
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private TextureView.SurfaceTextureListener h264decodecviewTL = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };
    private Camera.PreviewCallback mPreviewCallBack = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {

            byte[] i420bytes = new byte[bytes.length];
            //from YV20 to i420
            System.arraycopy(bytes, 0, i420bytes, 0, mPreviewWidth * mPreviewHeight);
            System.arraycopy(bytes, mPreviewWidth * mPreviewHeight + mPreviewWidth * mPreviewHeight / 4, i420bytes, mPreviewWidth * mPreviewHeight, mPreviewWidth * mPreviewHeight / 4);
            System.arraycopy(bytes, mPreviewWidth * mPreviewHeight, i420bytes, mPreviewWidth * mPreviewHeight + mPreviewWidth * mPreviewHeight / 4, mPreviewWidth * mPreviewHeight / 4);
            //用到了这个mVideoEncoder类的获取数据方法，把数据放到解码器
            if(mVideoEncoder != null) {
                mVideoEncoder.inputFrameToEncoder(i420bytes);
            }
          //  boolean inputResult = mInputDatasQueue.offer(i420bytes);
        }
    };

    private void openCamera(SurfaceTexture texture,int width, int height){
        {
           // Camera mCamera = null;
            mCamera = Camera.open(0);
            try {
                mCamera.setPreviewTexture(texture);
                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setPreviewFormat(ImageFormat.YV12);
                List<Camera.Size> list = parameters.getSupportedPreviewSizes();
                for (Camera.Size size : list) {
                    System.out.println("----size width = " + size.width + " size height = " + size.height);
                }

                mPreviewWidth = 640;
                mPreviewHeight = 480;
                parameters.setPreviewSize(mPreviewWidth, mPreviewHeight);
                mCamera.setParameters(parameters);
                mCamera.setPreviewCallback(mPreviewCallBack);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.e("CAM---------", Log.getStackTraceString(e));
                mCamera = null;
            }
        }

    }

}
