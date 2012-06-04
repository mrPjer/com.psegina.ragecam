package com.psegina.ragecam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.media.FaceDetector;
import android.media.FaceDetector.Face;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main application activity
 * 
 * @author Petar Šegina <psegina@ymail.com>
 *
 */
public class RageCamActivity extends Activity implements PreviewCallback,
		SurfaceHolder.Callback, OnItemClickListener {

	private static final int NUM_FACES = 1;

	private Camera cam = Camera.open(0);
	// private Vector<String> resolutions = new Vector<String>();
	// private Spinner resolutionSelector;
	private ListView imageList;
	private SurfaceView cameraView;
	private SurfaceHolder holder;
	private String[] images;
	private TextView fps;
	private long lastUpdate = 0, lastFPSupdate = 0;
	int fps_sum = 0, fps_count = 0;
	private DecimalFormat df = new DecimalFormat("00.00");
	private FaceDetector fd = new FaceDetector(160, 120, NUM_FACES);
	private Face[] faces = new FaceDetector.Face[NUM_FACES];
	private PointF eyes = new PointF();
	private ImageView frame;
	BitmapFactory.Options opt = new BitmapFactory.Options();
	private DetectTask dtask;

	private class DetectTask extends AsyncTask<byte[], Void, Face> {

		@Override
		protected Face doInBackground(byte[]... data) {
			byte[] d = data[0];

			int width = 640;
			int height = 480;

			int format = ImageFormat.NV21;

			Bitmap b = null;

			// YUV formats require more conversion
			if (format == ImageFormat.NV21 || format == ImageFormat.YUY2
					|| format == ImageFormat.NV16) {
				// Get the YuV image
				YuvImage yuv_image = new YuvImage(d, format, width, height, null);
				// Convert YuV to Jpeg
				Rect rect = new Rect(0, 0, width, height);
				ByteArrayOutputStream output_stream = new ByteArrayOutputStream();
				yuv_image.compressToJpeg(rect, 25, output_stream);
				// Convert from Jpeg to Bitmap

				b = BitmapFactory.decodeByteArray(output_stream.toByteArray(),
						0, output_stream.size(), opt);
			}
			// Jpeg and RGB565 are supported by BitmapFactory.decodeByteArray
			else if (format == ImageFormat.JPEG
					|| format == ImageFormat.RGB_565) {
				b = BitmapFactory.decodeByteArray(d, 0, data.length);
			}

			if (fd.findFaces(b, faces) > 0) {
				return faces[0];
			} else
				return null;
		}

		protected void onPostExecute(Face f) {
			if (f != null) {
				f.getMidPoint(eyes);
				frame.setLeft(640 - (int) eyes.x * 4 - 450);
				frame.setTop((int) eyes.y * 4 - 300);
			}
			fps_sum += System.currentTimeMillis() - lastUpdate;
			fps_count++;
			lastUpdate = System.currentTimeMillis();
		}

	}

	private class RageFaceAdapter extends BaseAdapter {

		private class ViewHolder {
			public ImageView image;
		}

		private AssetManager am = getAssets();
		private String[] paths;
		private ViewHolder tHolder;

		public RageFaceAdapter(String[] paths) {
			this.paths = paths;
		}

		@Override
		public int getCount() {
			return paths.length;
		}

		@Override
		public String getItem(int position) {
			return paths[position];
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = getLayoutInflater().inflate(R.layout.imageitem,
						null, false);
				tHolder = new ViewHolder();
				tHolder.image = (ImageView) convertView
						.findViewById(R.id.face_item);
				convertView.setTag(tHolder);
			}
			tHolder = (ViewHolder) convertView.getTag();
			try {
				tHolder.image.setImageBitmap(BitmapFactory.decodeStream(am
						.open("faces/" + paths[position])));
			} catch (IOException e) {
				Toast.makeText(RageCamActivity.this, e.toString(),
						Toast.LENGTH_SHORT).show();
				e.printStackTrace();
			}

			return convertView;
		}

	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		opt.inPreferredConfig = Bitmap.Config.RGB_565;
		opt.inSampleSize = 4;
		opt.inDither = false;
		opt.inPreferQualityOverSpeed = false;
		// resolutionSelector = (Spinner)
		// findViewById(R.id.resolution_selector);
		imageList = (ListView) findViewById(R.id.face_list);
		cameraView = (SurfaceView) findViewById(R.id.camera);
		fps = (TextView) findViewById(R.id.fps);
		holder = cameraView.getHolder();
		holder.addCallback(this);
		holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		holder.setKeepScreenOn(true);
		frame = (ImageView) findViewById(R.id.frame);

		imageList.setOnItemClickListener(this);

		if (cam != null) {
			cam.setPreviewCallback(this);

			Parameters params = cam.getParameters();
			// for (Camera.Size size : params.getSupportedPictureSizes()) {
			// resolutions.add(size.width + "x" + size.height);
			// }

			// resolutionSelector.setAdapter(new ArrayAdapter<String>(
			// getApplicationContext(),
			// android.R.layout.simple_list_item_1, resolutions));

			params.setPreviewSize(640, 480);
			cam.setParameters(params);

		}

		try {
			images = getAssets().list("faces");
		} catch (IOException e) {
			Toast.makeText(this,
					"Greška prilikom učitavanja slika\n" + e.toString(),
					Toast.LENGTH_LONG).show();
		}

		imageList.setAdapter(new RageFaceAdapter(images));

	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if(System.currentTimeMillis() - lastFPSupdate > 1000 && fps_count > 0){
			lastFPSupdate = System.currentTimeMillis();
			fps.setText(df.format(1000f / ((float) fps_sum / fps_count)));
			fps_sum = 0;
			fps_count = 0;
		}
		if (dtask == null || dtask.getStatus() == AsyncTask.Status.FINISHED) {
			dtask = new DetectTask();
			dtask.execute(data);
		}

	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		cam.startPreview();
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (cam != null) {
			try {
				cam.setPreviewDisplay(holder);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (cam != null) {
			cam.stopPreview();
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
		String path = "faces/"
				+ ((RageFaceAdapter) arg0.getAdapter()).getItem(arg2);
		try {
			frame.setImageBitmap(BitmapFactory.decodeStream(getAssets().open(
					path)));
		} catch (IOException e) {
			Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show();
			e.printStackTrace();
		}
	}

}