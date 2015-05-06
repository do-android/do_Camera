package doext.implement;

import java.io.ByteArrayOutputStream;
import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import core.DoServiceContainer;
import core.helper.DoIOHelper;
import core.helper.DoImageHandleHelper;
import core.helper.DoTextHelper;
import core.helper.jsonparse.DoJsonNode;
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
	public boolean invokeSyncMethod(String _methodName, DoJsonNode _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult) throws Exception {
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
	public boolean invokeAsyncMethod(String _methodName, DoJsonNode _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		if ("capture".equals(_methodName)) {
			this.capture(_dictParas, _scriptEngine, _callbackFuncName);
			return true;
		}
		return super.invokeAsyncMethod(_methodName, _dictParas, _scriptEngine, _callbackFuncName);
	}

	@Override
	public void capture(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine, String _callbackFuncName) throws Exception {
		try {
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
		private String callbackFuncName;
		private DoIScriptEngine scriptEngine;
		private String picTempPath;
		private DoIPageView activity;

		public void init(DoJsonNode _dictParas, DoIScriptEngine _scriptEngine, DoInvokeResult _invokeResult, String _callbackFuncName) throws Exception {
			this.scriptEngine = _scriptEngine;
			// 图片宽度
			this.width = _dictParas.getOneInteger("width", -1);
			// 图片高度
			this.height = _dictParas.getOneInteger("height", -1);
			// 清晰度1-100
			this.quality = _dictParas.getOneInteger("quality", 100);
			quality = quality > 100 ? 100 : quality;
			quality = quality < 1 ? 1 : quality;
			// 是否启动中间裁剪界面
			this.iscut = _dictParas.getOneBoolean("iscut", false);
			// 回调函数
			this.callbackFuncName = _callbackFuncName;

			activity = _scriptEngine.getCurrentPage().getPageView();
			activity.registActivityResultListener(this);
			// 照相机拍照
			Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
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
					DoServiceContainer.getLogEngine().writeInfo("取消拍照", "info");
				} else if ((requestCode == CameraCode && resultCode == Activity.RESULT_OK) || (requestCode == CutCode)) {
					if (this.iscut && requestCode == CameraCode) {
						Intent intentCrop = new Intent("com.android.camera.action.CROP");
						intentCrop.setDataAndType(imageUri, "image/*");
						intentCrop.putExtra("crop", "true");
						// aspectX aspectY 是宽高的比例
						if (this.width <= 0 || this.height <= 0) {
							intentCrop.putExtra("aspectX", 1);
							intentCrop.putExtra("aspectY", 1);
						} else {
							intentCrop.putExtra("aspectX", this.width);
							intentCrop.putExtra("aspectY", this.height);
						}
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
						new CameraSaveTask(activity, this, callbackFuncName, bitmapPath).execute(new String[] {});
					}
				}
			} catch (Exception _err) {
				DoServiceContainer.getLogEngine().writeError("do_Camera_Model", _err);
			}
		}

		class CameraSaveTask extends AsyncTask<String, Void, String> {

			private String callbackFuncName;
			private DoIPageView activity;
			private DoActivityResultListener doActivityResultListener;
			private String bitmapPath;

			public CameraSaveTask(DoIPageView activity, DoActivityResultListener resultListener, String callbackFuncName, String bitmapPath) {
				this.callbackFuncName = callbackFuncName;
				this.activity = activity;
				this.doActivityResultListener = resultListener;
				this.bitmapPath = bitmapPath;
			}

			@Override
			protected String doInBackground(String... params) {
				DoInvokeResult invokeResult = new DoInvokeResult(getUniqueKey());
				ByteArrayOutputStream photo_data = new ByteArrayOutputStream();
				String _fileName = DoTextHelper.getTimestampStr() + ".png.do";
				String _fileFullName = scriptEngine.getCurrentApp().getDataFS().getRootPath() + "/temp/do_Camera/" + _fileName;
				Bitmap bitmap = null;
				try {
					bitmap = DoImageHandleHelper.resizeScaleImage(this.bitmapPath, width, height);
					if (bitmap != null) {
						bitmap.compress(Bitmap.CompressFormat.JPEG, quality, photo_data);
					}
					DoIOHelper.writeAllBytes(_fileFullName, photo_data.toByteArray());
					String _url = "data://temp/do_Camera/" + _fileName;
					File photo = new File(picTempPath);
					photo.delete();
					invokeResult.setResultText(_url);
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
}
