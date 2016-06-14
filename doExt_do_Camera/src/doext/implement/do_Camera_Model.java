package doext.implement;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import org.json.JSONObject;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import core.DoServiceContainer;
import core.helper.DoIOHelper;
import core.helper.DoImageHandleHelper;
import core.helper.DoJsonHelper;
import core.helper.DoTextHelper;
import core.interfaces.DoActivityResultListener;
import core.interfaces.DoIPageView;
import core.interfaces.DoIScriptEngine;
import core.object.DoInvokeResult;
import core.object.DoSingletonModule;
import doext.define.do_Camera_IMethod;

/**
 * 自定义扩展SM组件Model实现，继承DoSingletonModule抽象类，并实现Do_Camera_IMethod接口方法；
 * #如何调用组件自定义事件？可以通过如下方法触发事件：
 * this.model.getEventCenter().fireEvent(_messageName, jsonResult);
 * 参数解释：@_messageName字符串事件名称，@jsonResult传递事件参数对象； 获取DoInvokeResult对象方式new
 * DoInvokeResult(this.getUniqueKey());
 */
public class do_Camera_Model extends DoSingletonModule implements do_Camera_IMethod {

	private int cammeraIndex = -1;

	public do_Camera_Model() throws Exception {
		super();
	}

	/**
	 * 同步方法，JS脚本调用该组件对象方法时会被调用，可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V）
	 * @_scriptEngine 当前Page JS上下文环境对象
	 * @_invokeResult 用于返回方法结果对象
	 */
	@Override
	public boolean invokeSyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
		// ...do something
		return super.invokeSyncMethod(_methodName, _dictParas, _scriptEngine, _invokeResult);
	}

	/**
	 * 异步方法（通常都处理些耗时操作，避免UI线程阻塞），JS脚本调用该组件对象方法时会被调用， 可以根据_methodName调用相应的接口实现方法；
	 * 
	 * @_methodName 方法名称
	 * @_dictParas 参数（K,V）
	 * @_scriptEngine 当前page JS上下文环境
	 * @_callbackFuncName 回调函数名 #如何执行异步方法回调？可以通过如下方法：
	 *                    _scriptEngine.callback(_callbackFuncName,
	 *                    _invokeResult);
	 *                    参数解释：@_callbackFuncName回调函数名，@_invokeResult传递回调函数参数对象；
	 *                    获取DoInvokeResult对象方式new
	 *                    DoInvokeResult(this.getUniqueKey());
	 */
	@Override
	public boolean invokeAsyncMethod(String _methodName, JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		if ("capture".equals(_methodName)) {
			this.capture(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		return super.invokeAsyncMethod(_methodName, _dictParas, _scriptEngine, _callbackFuncName);
	}

	@Override
	public void capture(JSONObject _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		try {
			//启动前置摄像头
			boolean isFacingFront = DoJsonHelper.getBoolean(_dictParas, "facingFront", false);
			if (isFacingFront) {
				cammeraIndex = findFrontCamera();
				if (cammeraIndex == -1) {
					cammeraIndex = findBackCamera();
				}
			} else {
				cammeraIndex = findBackCamera();
				if (cammeraIndex == -1) {
					cammeraIndex = findFrontCamera();
				}
			}
			if (cammeraIndex == -1) {
				throw new Exception("无法打开系统摄像头！");
			}

			DoInvokeResult _invokeResult = new DoInvokeResult(this.getUniqueKey());
			CameraCaptureListener _myListener = new CameraCaptureListener();
			_myListener.init(_dictParas, _scriptEngine, _invokeResult, _callbackFuncName);
		} catch (Exception _err) {
			DoServiceContainer.getLogEngine().writeError("DoCamera capture \n", _err);
		}
	}

	private class CameraCaptureListener implements DoActivityResultListener {

		private final int CameraCode = 10001;
		private final int CutCode = 10002;
		private Uri imageUri;
		private int width;
		private int height;
		private int quality;
		private boolean iscut;
		private String outPath;
		private String callbackFuncName;
		private DoIScriptEngine scriptEngine;
		private String picTempPath;
		private DoIPageView activity;

		public void init(JSONObject _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult, String _callbackFuncName) throws Exception {
			this.scriptEngine = _scriptEngine;
			// 图片宽度
			this.width = DoJsonHelper.getInt(_dictParas, "width", -1);
			// 图片高度
			this.height = DoJsonHelper.getInt(_dictParas, "height", -1);
			// 清晰度1-100
			this.quality = DoJsonHelper.getInt(_dictParas, "quality", 100);
			quality = quality > 100 ? 100 : quality;
			quality = quality < 1 ? 1 : quality;
			// 是否启动中间裁剪界面
			this.iscut = DoJsonHelper.getBoolean(_dictParas, "iscut", false);

			this.outPath = DoJsonHelper.getString(_dictParas, "outPath", "");
			// 回调函数
			this.callbackFuncName = _callbackFuncName;

			activity = _scriptEngine.getCurrentPage().getPageView();
			activity.registActivityResultListener(this);
			// 照相机拍照
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
			intent.putExtra("android.intent.extras.CAMERA_FACING", cammeraIndex);
			picTempPath = ((Activity) activity).getExternalCacheDir() + "/" + DoTextHelper.getTimestampStr() + ".jpg";
			File photo = new File(picTempPath);
			imageUri = Uri.fromFile(photo);
			intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
			((Activity) activity).startActivityForResult(intent, CameraCode);
		}

		@Override
		public void onActivityResult(int requestCode, int resultCode, Intent intent) {
			try {
				// 回调
				if ((requestCode == CameraCode || requestCode == CutCode) && resultCode == Activity.RESULT_CANCELED) { // 取消
					activity.unregistActivityResultListener(this);
					DoServiceContainer.getLogEngine().writeInfo("取消拍照", "info");
				} else if ((requestCode == CameraCode && resultCode == Activity.RESULT_OK) || (requestCode == CutCode)) {
					if (this.iscut && requestCode == CameraCode) {
						Intent intentCrop = new Intent("com.android.camera.action.CROP");
						intentCrop.setDataAndType(imageUri, "image/*");
						intentCrop.putExtra("crop", "true");
						// aspectX aspectY 是宽高的比例
//						if (this.width <= 0 || this.height <= 0) {
//							intentCrop.putExtra("aspectX", 1);
//							intentCrop.putExtra("aspectY", 1);
//						} else {
//							intentCrop.putExtra("aspectX", this.width);
//							intentCrop.putExtra("aspectY", this.height);
//						}
						// outputX outputY 是裁剪图片宽高
//						intentCrop.putExtra("outputX", this.width);
//						intentCrop.putExtra("outputY", this.height);
						intentCrop.putExtra("scale", true);
						intentCrop.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(Environment.getExternalStorageDirectory() + "/temp.jpg")));
						intentCrop.putExtra("return-data", false);
						intentCrop.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
						intentCrop.putExtra("noFaceDetection", true);
						((Activity) activity).startActivityForResult(intentCrop, CutCode);
					} else {
						String bitmapPath = imageUri.getPath();
						if (this.iscut) {
							bitmapPath = Environment.getExternalStorageDirectory() + "/temp.jpg";
						}
						new CameraSaveTask(activity, this, callbackFuncName, bitmapPath, outPath).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, new String[] {});
					}
				}
			} catch (Exception _err) {
				DoServiceContainer.getLogEngine().writeError("do_Camera_Model", _err);
			}
		}

		private class CameraSaveTask extends AsyncTask<String, Void, String> {

			private String callbackFuncName;
			private DoIPageView activity;
			private DoActivityResultListener doActivityResultListener;
			private String bitmapPath;
			private String outPath;

			public CameraSaveTask(DoIPageView activity, DoActivityResultListener resultListener, String callbackFuncName, String bitmapPath, String outPath) {
				this.callbackFuncName = callbackFuncName;
				this.activity = activity;
				this.doActivityResultListener = resultListener;
				this.bitmapPath = bitmapPath;
				this.outPath = outPath;
			}

			@Override
			protected String doInBackground(String... params) {
				boolean _isUseDefault = false;
				DoInvokeResult invokeResult = new DoInvokeResult(getUniqueKey());
				ByteArrayOutputStream photo_data = new ByteArrayOutputStream();
				String _fileName = DoTextHelper.getTimestampStr() + ".png.do";

				String _fillPath = "";
				try {
					_fillPath = DoIOHelper.getLocalFileFullPath(scriptEngine.getCurrentPage().getCurrentApp(), outPath);
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (TextUtils.isEmpty(_fillPath)) {
					_isUseDefault = true;
					_fillPath = scriptEngine.getCurrentApp().getDataFS().getRootPath() + "/temp/do_Camera/" + _fileName;
				}

				Bitmap bitmap = null;
				try {
					if (!DoIOHelper.existFile(_fillPath)) {
						DoIOHelper.createFile(_fillPath);
					}
					bitmap = DoImageHandleHelper.resizeScaleImage(this.bitmapPath, width, height);
					if (bitmap != null) {
						int degree = getBitmapDegree(this.bitmapPath);
						bitmap = rotateBitmapByDegree(bitmap, degree);
						bitmap.compress(Bitmap.CompressFormat.JPEG, quality, photo_data);
					}

					DoIOHelper.writeAllBytes(_fillPath, photo_data.toByteArray());
					File photo = new File(picTempPath);
					photo.delete();

					String _resultText = outPath;
					if (_isUseDefault) {
						_resultText = "data://temp/do_Camera/" + _fileName;
					}
					invokeResult.setResultText(_resultText);
					scriptEngine.callback(this.callbackFuncName, invokeResult);
				} catch (Exception _err) {
					DoServiceContainer.getLogEngine().writeError("do_Camera_Model", _err);
				} finally {
					if (bitmap != null) {
						bitmap.recycle();
						bitmap = null;
					}
					System.gc();
					activity.unregistActivityResultListener(doActivityResultListener);
				}
				return null;
			}
		}

	}

	/**
	 * 读取图片的旋转的角度
	 * 
	 * @param path
	 *            图片绝对路径
	 * @return 图片的旋转角度
	 */
	private int getBitmapDegree(String path) {
		int degree = 0;
		try {
			// 从指定路径下读取图片，并获取其EXIF信息
			ExifInterface exifInterface = new ExifInterface(path);
			// 获取图片的旋转信息
			int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
			switch (orientation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				degree = 90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				degree = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				degree = 270;
				break;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return degree;
	}

	/**
	 * 将图片按照某个角度进行旋转
	 * 
	 * @param bm
	 *            需要旋转的图片
	 * @param degree
	 *            旋转角度
	 * @return 旋转后的图片
	 */
	private Bitmap rotateBitmapByDegree(Bitmap bm, int degree) {
		Bitmap returnBm = null;

		// 根据旋转角度，生成旋转矩阵
		Matrix matrix = new Matrix();
		matrix.postRotate(degree);
		try {
			// 将原始图片按照旋转矩阵进行旋转，并得到新的图片
			returnBm = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
		} catch (OutOfMemoryError e) {
		}
		if (returnBm == null) {
			returnBm = bm;
		}
		if (bm != returnBm) {
			bm.recycle();
		}
		return returnBm;
	}

	private int findFrontCamera() {
		int cameraCount = 0;
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		cameraCount = Camera.getNumberOfCameras(); // get cameras number  

		for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
			Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo  
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
				// 代表摄像头的方位，目前有定义值两个分别为CAMERA_FACING_FRONT前置和CAMERA_FACING_BACK后置  
				return camIdx;
			}
		}
		return -1;
	}

	private int findBackCamera() {
		int cameraCount = 0;
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		cameraCount = Camera.getNumberOfCameras(); // get cameras number  

		for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
			Camera.getCameraInfo(camIdx, cameraInfo); // get camerainfo  
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				// 代表摄像头的方位，目前有定义值两个分别为CAMERA_FACING_FRONT前置和CAMERA_FACING_BACK后置  
				return camIdx;
			}
		}
		return -1;
	}
}
