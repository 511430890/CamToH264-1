package com.xair.h264demo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	//private String h264Path = "/storage/emulated/0/single_720.bin";
//	private File h264File = new File(rootpath+h264Path);
	//private String h264Path =  "/storage/emulated/0/8610.h264";
    private String h264Path =  "/storage/emulated/0/720pq.h264";
	private String rootpath = Environment.getExternalStorageDirectory().getAbsolutePath();  //路径
	private File h264File = new File(h264Path);
	private InputStream is = null;
	private FileInputStream fs = null;

	private SurfaceView mSurfaceView;
	private Button mReadButton;
	private MediaCodec mCodec;

	Thread readFileThread;
	boolean isInit = false;

	// Video Constants
	private final static String MIME_TYPE = "video/avc"; // H.264 Advanced Video
	private final static int VIDEO_WIDTH = 1280;
	private final static int VIDEO_HEIGHT = 720;
	private final static int TIME_INTERNAL = 30;
	//private final static int HEAD_OFFSET = 512;
    private final static int HEAD_OFFSET = 0;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
		mReadButton = (Button) findViewById(R.id.btn_readfile);
		mReadButton.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				if (h264File.exists()) {
					if (!isInit) {
						initDecoder();
						isInit = true;
					}

					readFileThread = new Thread(readFile);
					readFileThread.start();
				} else {
					Toast.makeText(getApplicationContext(),
							"H264 file not found", Toast.LENGTH_SHORT).show();
				}
			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		readFileThread.interrupt();
	}

	public void initDecoder() {

		mCodec = MediaCodec.createDecoderByType(MIME_TYPE);
		MediaFormat mediaFormat = MediaFormat.createVideoFormat(MIME_TYPE,
				VIDEO_WIDTH, VIDEO_HEIGHT);
		mCodec.configure(mediaFormat, mSurfaceView.getHolder().getSurface(),
				null, 0);
		mCodec.start();
	}

	int mCount = 0;

	public boolean onFrame(byte[] buf, int offset, int length) {
		Log.e("Media", "onFrame start");
		Log.e("Media", "onFrame Thread:" + Thread.currentThread().getId());
		// Get input buffer index
		ByteBuffer[] inputBuffers = mCodec.getInputBuffers();
		int inputBufferIndex = mCodec.dequeueInputBuffer(100);

		Log.e("Media", "onFrame index:" + inputBufferIndex);
		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
			inputBuffer.clear();
			inputBuffer.put(buf, offset, length);
			mCodec.queueInputBuffer(inputBufferIndex, 0, length, mCount
					* TIME_INTERNAL, 0);
			mCount++;
		} else {
			return false;
		}

		// Get output buffer index
		MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 100);
		while (outputBufferIndex >= 0) {
			mCodec.releaseOutputBuffer(outputBufferIndex, true);
			outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 0);
		}
		Log.e("Media", "onFrame end");
		return true;
	}

	/**
	 * Find H264 frame head
	 * 
	 * @param buffer
	 * @param len
	 * @return the offset of frame head, return 0 if can not find one
	 */
	static int findHead(byte[] buffer, int len) {
		int i;
		for (i = HEAD_OFFSET; i < len; i++) {
			if (checkHead(buffer, i))
				break;
		}
		if (i == len)
			return 0;
		if (i == HEAD_OFFSET)
			return 0;
		return i;
	}

	/**
	 * Check if is H264 frame head
	 * 
	 * @param buffer
	 * @param offset
	 * @return whether the src buffer is frame head
	 */
	static boolean checkHead(byte[] buffer, int offset) {
		// 00 00 00 01
		if (buffer[offset] == 0 && buffer[offset + 1] == 0
				&& buffer[offset + 2] == 0 && buffer[offset +3] == 1)
			return true;
		// 00 00 01
		if (buffer[offset] == 0 && buffer[offset + 1] == 0
				&& buffer[offset + 2] == 1)
			return true;
		return false;
	}

	Runnable readFile = new Runnable() {

		@Override
		public void run() {
			int h264Read = 0;
			int frameOffset = 0;
			byte[] buffer = new byte[100000];
			byte[] framebuffer = new byte[200000];
			boolean readFlag = true;
			try {
				fs = new FileInputStream(h264File);
				is = new BufferedInputStream(fs);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
			while (!Thread.interrupted() && readFlag) {
				try {
					int length = is.available();
					if (length > 0) {
						// Read file and fill buffer
                        Log.i("length", "" + length);
						int count = is.read(buffer);
						Log.i("count", "" + count);
						h264Read += count;
						Log.d("Read", "count:" + count + " h264Read:"
								+ h264Read);
						// Fill frameBuffer
						if (frameOffset + count < 200000) {
							System.arraycopy(buffer, 0, framebuffer,
									frameOffset, count);
							frameOffset += count;
                            Log.d("frameOffset", "frameOffset"+frameOffset);
						} else {
							frameOffset = 0;
							System.arraycopy(buffer, 0, framebuffer,
									frameOffset, count);
							frameOffset += count;
						}

						// Find H264 head
						int offset = findHead(framebuffer, frameOffset);
						Log.i("find head", " Head:" + offset);
						while (offset > 0) {
							if (checkHead(framebuffer, offset)) {
								// Fill decoder
								boolean flag = onFrame(framebuffer, offset, h264Read-offset);
								if (flag) {
									byte[] temp = framebuffer;
									framebuffer = new byte[200000];
									System.arraycopy(temp, offset, framebuffer,
											0, frameOffset - offset);
									frameOffset -= offset;
									Log.e("Check", "is Head:" + offset);
									// Continue finding head
									offset = findHead(framebuffer, frameOffset);
								}
							} else {

								offset = 0;
							}

						}
						Log.d("loop", "end loop");
					} else {
						h264Read = 0;
						frameOffset = 0;
						readFlag = false;
						// Start a new thread
                        Log.d("read length", "read length = 0"+length);
						readFileThread = new Thread(readFile);
						readFileThread.start();
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

				try {
					Thread.sleep(TIME_INTERNAL);
				} catch (InterruptedException e) {

				}
			}
		}
	};
}
