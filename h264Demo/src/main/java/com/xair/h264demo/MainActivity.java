package com.xair.h264demo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	private String h264Path =  "/storage/emulated/0/n1080.h264";
	//private String h264Path =  "/storage/emulated/0/single_720.bin";
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
	private final static int VIDEO_WIDTH = 720;
	private final static int VIDEO_HEIGHT = 576;
	private final static int TIME_INTERNAL = 30;
	//为了找到I帧结尾？HEAD_OFFSET设置偏移那么大
	private final static int HEAD_OFFSET = 512;
	//private final static int HEAD_OFFSET = 0;

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
		//getSupportColorFormat();
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
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE,20);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE,15);
		//mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar);
//		byte[] header_sps = {0, 0, 0, 1, 103, 66, 0 , 41, -115, -115, 64, 80 , 30 , -48 , 15 ,8,-124, 83, -128};
//
//		byte[] header_pps = {0,0 ,0, 1, 104, -54, 67, -56};
//
//		mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
//		mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));
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

		Log.e("Media", "onFrame index:-------inputBufferIndex=" + inputBufferIndex);
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
		int outputBufferIndex = mCodec.dequeueOutputBuffer(bufferInfo, 10000);
		while (outputBufferIndex >= 0) {
			Log.e("Media", "onFrame index:-------OUTputBufferIndex=" + outputBufferIndex);
			Log.e("Media", "onFrame -----bufferInfo " + bufferInfo.flags);
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
				&& buffer[offset + 2] == 0 && buffer[offset+3] == 1)
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
							if (checkHead(framebuffer, 0)) {
//								// Fill decoder
								//第一次把I帧包括AUD，SPS，PPS都放进去了。
								boolean flag = onFrame(framebuffer, 0, offset);
								if (flag) {
									byte[] temp = framebuffer;
									framebuffer = new byte[200000];
									//把framebuffer减去存在缓冲区那一帧后，再作为一个新帧送到缓冲区
									System.arraycopy(temp, offset, framebuffer,
											0, frameOffset - offset);
									//减去后，这个帧数组总大小就少了offset个
									frameOffset -= offset;
									Log.e("Check", "is Head:" + offset+"frameOffset"+frameOffset);
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
	private int getSupportColorFormat() {
		int numCodecs = MediaCodecList.getCodecCount();
		MediaCodecInfo codecInfo = null;
		for (int i = 0; i < numCodecs && codecInfo == null; i++) {
			MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
			if (!info.isEncoder()) {
				continue;
			}
			String[] types = info.getSupportedTypes();
			boolean found = false;
			for (int j = 0; j < types.length && !found; j++) {
				if (types[j].equals("video/avc")) {
					System.out.println("found");
					found = true;
				}
			}
			if (!found)
				continue;
			codecInfo = info;
		}

		Log.e("AvcEncoder", "Found " + codecInfo.getName() + " supporting " + "video/avc");

		// Find a color profile that the codec supports
		MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
		Log.e("AvcEncoder",
				"length-" + capabilities.colorFormats.length + "==" + Arrays.toString(capabilities.colorFormats));

		for (int i = 0; i < capabilities.colorFormats.length; i++) {

			switch (capabilities.colorFormats[i]) {
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
//				case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
//					Log.e("AvcEncoder", "supported color format::" + capabilities.colorFormats[i]);
//					break;

				default:
					Log.e("AvcEncoder", "other color format " + capabilities.colorFormats[i]);
					break;
			}
		}
		//return capabilities.colorFormats[i];
		return 0;
	}
}
