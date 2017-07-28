package com.why.project.cachedemo;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.why.project.cachedemo.utils.cache.Cache;

import java.net.URL;

public class MainActivity extends AppCompatActivity {

	private Button btn_save;
	private Button btn_show;
	private Button btn_getsize;
	private Button btn_remove;

	private TextView tv_size;
	private ImageView img_show;

	private GetImgToSaveCacheTask getImgToSaveCacheTask;//获取图片并保存到缓存的异步请求类

	/**内存、磁盘缓存*/
	private Cache mCache;
	String imgUrl = "http://img.ithome.com/newsuploadfiles/2014/12/20141223_115629_592.jpg";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/**必须在使用之前初始化*/
		mCache = new Cache(this);
		mCache.initMemoryCache();
		mCache.initDiskCache();

		initViews();
		initEvents();
	}

	@Override
	public void onPause() {
		super.onPause();

		//cancel方法只是将对应的AsyncTask标记为cancel状态，并不是真正的取消线程的执行，在Java中并不能粗暴的停止线程，只能等线程执行完之后做后面的操作
		if(getImgToSaveCacheTask != null && getImgToSaveCacheTask.getStatus() == AsyncTask.Status.RUNNING){
			getImgToSaveCacheTask.cancel(true);
		}

		mCache.clearCache();
		System.gc();
	}

	private void initViews() {
		btn_save = (Button) findViewById(R.id.btn_save);
		btn_show = (Button) findViewById(R.id.btn_show);
		btn_getsize = (Button) findViewById(R.id.btn_getsize);
		btn_remove = (Button) findViewById(R.id.btn_remove);

		tv_size = (TextView) findViewById(R.id.tv_size);
		img_show = (ImageView) findViewById(R.id.img_show);
	}

	private void initEvents() {
		btn_save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				saveToCache(imgUrl);
			}
		});

		btn_show.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				showImg(imgUrl);
			}
		});

		btn_getsize.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String cacheSize = mCache.getSize();
				tv_size.setText(tv_size.getText() + cacheSize);
			}
		});

		btn_remove.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mCache.removeFromCache(mCache.hashKeyForDisk(imgUrl));
			}
		});
	}

	/**显示图片*/
	private void showImg(String imgUrl) {
		String cacheKey = mCache.hashKeyForDisk(imgUrl);
		Bitmap bitmap = mCache.getBitmapFromCache(cacheKey);//从缓存中获取图片bitmap
		if(bitmap != null){
			BitmapDrawable drawable = null;
			Resources resources = getResources();
			drawable = new BitmapDrawable(resources, bitmap);
			//设置背景图片
			if(Build.VERSION.SDK_INT >= 16) {
				img_show.setBackground(drawable);
			}else{
				img_show.setBackgroundDrawable(drawable);
			}
		}
	}

	/**保存图标到缓存中*/
	private void saveToCache(String imgUrl){

		//如果已经下载过来，那么不需要重新下载
		String cacheKey = mCache.hashKeyForDisk(imgUrl);
		Bitmap bitmap = mCache.getBitmapFromCache(cacheKey);//从缓存中获取图片bitmap
		if(bitmap == null){
			//判断是否有网络，此处注释掉
			//if (HttpUtil.isNetworkAvailable(MainActivity.this)) {
				//执行异步任务
				GetImgToSaveCacheTask getImgTask = new GetImgToSaveCacheTask();
				getImgTask.execute(imgUrl);
			/*} else {
				this.showShortToast(getResources().getString(R.string.httpError));
			}*/
		}
	}

	/**
	 * 保存图标异步请求类
	 */
	public class GetImgToSaveCacheTask extends AsyncTask<String, Void, String> {

		@Override
		protected void onPreExecute() {
			//showProgressDialog("");//显示加载对话框
		}
		@Override
		protected String doInBackground(String... params) {
			String data = "";
			if(! isCancelled()){
				try {
					if(params[0].equals("")){
						data = "";
					}else{
						//下载图片并缓存
						BitmapFactory.Options opts = new BitmapFactory.Options();

						String cacheKey = mCache.hashKeyForDisk(params[0]);
						Bitmap bitmap = null;
						synchronized(mCache.mDiskCacheLock){
							if(mCache.getBitmapFromCache(cacheKey) == null) {
								//如果缓存中无图片，则添加到磁盘缓存和内存缓存中
								bitmap = BitmapFactory.decodeStream(new URL(params[0]).openConnection().getInputStream(), null, opts);
								mCache.addBitmapToMemoryCache(cacheKey, bitmap);
								mCache.addBitmapToDiskCache(cacheKey, bitmap);
							} else if(mCache.mMemoryCache.get(cacheKey) == null) {
								//如果内存缓存中无图片，则磁盘缓存中添加到内存缓存中
        						bitmap = mCache.getBitmapFromDiskCache(cacheKey);
        						mCache.addBitmapToMemoryCache(cacheKey, bitmap);
							} else if(mCache.getBitmapFromDiskCache(cacheKey) == null) {
								//如果磁盘缓存中无图片，则从内存缓存中添加到磁盘缓存中
        						bitmap = mCache.getBitmapFromMemCache(cacheKey);
        						mCache.addBitmapToDiskCache(cacheKey, bitmap);
							}
						}
						data = "success";
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return data;
		}
		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if(isCancelled()){
				return;
			}
			try {
				if (result != null && !"".equals(result)){
					//缓存成功，此处可以进行其他处理，比如显示图片
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				if(! isCancelled()){
					//dismissProgressDialog();//隐藏加载对话框
				}
			}
		}
	}
}
