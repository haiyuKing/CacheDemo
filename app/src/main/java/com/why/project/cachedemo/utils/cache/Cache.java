package com.why.project.cachedemo.utils.cache;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;

import static android.util.Log.d;

/**
 * Used 硬盘缓存工具类（包含内存缓存LruCache和磁盘缓存DiskLruCache）
 * http://blog.csdn.net/guolin_blog/article/details/28863651
 */
public class Cache {
	
	private static final String TAG = "Cache";
	private static final String DISK_CACHE_SUBDIR = "why";
	private static final int DISK_MAX_SIZE = 67108864;//磁盘缓存的最大值64MB
	private static final int MEM_MAX_SIZE = 33554432;//内存缓存的最大值32MB
	
	private String bitmapType;
	private Context mContext;
	private DiskLruCache mDiskCache;
	public final Object mDiskCacheLock = new Object();
	private boolean mDiskCacheStarting = true;
	public LruCache<String, Bitmap> mMemoryCache;

	public Cache(Context context)
	{
		this.mContext = context;
	}
	
	/**初始化磁盘缓存*/
	public void initDiskCache()
	{
		File localFile = getDiskCacheDir(this.mContext, DISK_CACHE_SUBDIR);
		new InitDiskCacheTask().execute(new File[] { localFile });
	}
	/**初始化内存缓存*/
	public void initMemoryCache()
	{
		this.mMemoryCache = new LruCache<String, Bitmap>(MEM_MAX_SIZE)
		{
			protected int sizeOf(String key, Bitmap bitmap)
			{
				return bitmap.getByteCount();
			}
		};
	}
	
	/**
	 * 获取当前app版本号
	 * @param context 上下文
	 * @return 当前app版本号
	 */
	private int getAppVersion(Context context)
	{
	    PackageManager manager = context.getPackageManager();
		int code = 1;
		try
		{
			code = manager.getPackageInfo(context.getPackageName(),0).versionCode;
		} catch (NameNotFoundException e)
		{
			e.printStackTrace();
		}
		return code;
	}
	
	/**
	 * 获取缓存文件路径(优先选择sd卡)
	 * @param cacheDirName 缓存文件夹名称
	 * @param context 上下文
	 * @return
	 */
	private static File getDiskCacheDir(Context context, String cacheDirName)
	{
	    String cacheDir;
		
		if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) && !Environment.isExternalStorageRemovable())
		{
			cacheDir = getExternalCacheDir(context);
			if(cacheDir == null)//部分机型返回了null
				cacheDir = getInternalCacheDir(context);
		}else
		{
			cacheDir = getInternalCacheDir(context);
		}
		File dir =  new File(cacheDir,cacheDirName);
		if(!dir.exists())
			dir.mkdirs();
		return dir;
	}
	
	private static String getExternalCacheDir(Context context)
	{
		File dir = context.getExternalCacheDir();
		if(dir == null)
			return null;
		if(!dir.exists())
			dir.mkdirs();
		return dir.getPath();
	}
	
	private static String getInternalCacheDir(Context context)
	{
		File dir = context.getCacheDir();
		if(!dir.exists())
			dir.mkdirs();
		return dir.getPath();
	}
	
	/**
	 * 根据原始键生成新键，以保证键的名称的合法性
	 * @param key 原始键，通常是url
	 * @return
	 */
	public String hashKeyForDisk(String key)
	{
	    String cacheKey = "";
		try
		{
			MessageDigest digest = MessageDigest.getInstance("md5");
			digest.update(key.getBytes());
			cacheKey = bytesToHexString(digest.digest());
			
		} catch (NoSuchAlgorithmException e)
		{
			e.printStackTrace();
			cacheKey = String.valueOf(key.hashCode());  
		}
		return cacheKey;
	}
	
	private String bytesToHexString(byte[] bytes)
	{
		StringBuilder sb = new StringBuilder();
	    for (int i = 0; i < bytes.length; i++) {  
	        String hex = Integer.toHexString(0xFF & bytes[i]);
	        if (hex.length() == 1) {  
	            sb.append('0');  
	        }  
	        sb.append(hex);  
	    }
	    
	    return sb.toString();
	}
	
	/**将Bitmap添加到LruCache和DiskLruCache中*/
	public void addBitmapToCache(String key, Bitmap bitmap)
	{
		addBitmapToMemoryCache(key, bitmap);
		addBitmapToDiskCache(key, bitmap);
	}

	/**将Bitmap添加到磁盘缓存中*/
	public void addBitmapToDiskCache(String key, Bitmap bitmap)
	{
		if (bitmap == null)
			return;
		try
		{
			synchronized (mDiskCacheLock)
			{
				if ((mDiskCache != null) && (mDiskCache.get(key) == null))
				{
					DiskLruCache.Editor localEditor = this.mDiskCache.edit(key);
					if (localEditor != null)
					{
						bitmapToStream(bitmap, localEditor.newOutputStream(0));
						localEditor.commit();
					}
				}
			}
		}
		catch (IllegalStateException localIllegalStateException)
		{
		}
		catch (IOException localIOException)
		{
		}
	}
	
	private void bitmapToStream(Bitmap bitmap, OutputStream paramOutputStream)
	{
		ByteArrayOutputStream localByteArrayOutputStream = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.PNG, 100, localByteArrayOutputStream);
		byte[] arrayOfByte = localByteArrayOutputStream.toByteArray();
		try
		{
			paramOutputStream.write(arrayOfByte);
			paramOutputStream.flush();
			paramOutputStream.close();
			return;
		}
		catch (IOException localIOException)
		{
		}
	}
	
	/**将Bitmap添加到内存缓存中*/
	public void addBitmapToMemoryCache(String key, Bitmap bitmap)
	{
		if (getBitmapFromMemCache(key) == null)
		{
			if ((key != null) && (bitmap != null)){
				this.mMemoryCache.put(key, bitmap);
			}
		}
	}
	/**清除缓存*/
	public void clearCache()
	{
		Log.w(TAG,"{clearCache}");
		try
		{
			if (this.mDiskCache != null)
			{
				d(TAG,"mDiskCache.size()1=" + mDiskCache.size());
				/*这个方法用于将内存中的操作记录同步到日志文件（也就是journal文件）当中。
				这个方法非常重要，因为DiskLruCache能够正常工作的前提就是要依赖于journal文件中的内容。
				前面在讲解写入缓存操作的时候我有调用过一次这个方法，但其实并不是每次写入缓存都要调用一次flush()方法的，频繁地调用并不会带来任何好处，只会额外增加同步journal文件的时间。
				比较标准的做法就是在Activity的onPause()方法中去调用一次flush()方法就可以了。*/
				this.mDiskCache.flush();
				d(TAG,"mDiskCache.size()2=" + mDiskCache.size());
			}
			if (mMemoryCache != null) {
				if (mMemoryCache.size() > 0) {
					d(TAG,"mMemoryCache.size()1=" + mMemoryCache.size());
					mMemoryCache.evictAll();
					Log.d(TAG, "mMemoryCache.size()2=" + mMemoryCache.size());
				}
			}
		}
		catch (IOException localIOException)
		{
		}
	}
	/**从缓存中获取Bitmap*/
	public Bitmap getBitmapFromCache(String key)
	{
		Bitmap localBitmap = getBitmapFromMemCache(key);
		if (localBitmap == null)
			localBitmap = getBitmapFromDiskCache(key);
		return localBitmap;
	}
	/**从内存缓存中获取Bitmap*/
	public Bitmap getBitmapFromMemCache(String key)
	{

		Bitmap bm = mMemoryCache.get(key);
		if (bm != null) {
			return bm;
		}
		return null;
	}
	/**从磁盘缓存中获取Bitmap*/
	public Bitmap getBitmapFromDiskCache(String key)
	{
		synchronized(mDiskCacheLock) {
            while(mDiskCacheStarting) {
                try {
                    mDiskCacheLock.wait();
                    continue;
                } catch(InterruptedException localInterruptedException1) {
                }
            }
            if(mDiskCache != null) {
                try {
                    DiskLruCache.Snapshot snapShot = mDiskCache.get(key);
                    if(snapShot != null) {
                        InputStream is = snapShot.getInputStream(0);
                        return BitmapFactory.decodeStream(is);
                    }
                } catch(IOException localIOException2) {
                }
                
            }
        }
        return null;
	}

	public String getBitmapType()
	{
		return this.bitmapType;
	}
	
	/**从缓存中移除cacheKey*/
	public void removeFromCache(String cacheKey)
	{
		try
		{
			if (cacheKey != null) {
				if (mMemoryCache != null) {
					Bitmap bm = mMemoryCache.remove(cacheKey);
					if (bm != null)
						bm.recycle();
				}
			}

			if (this.mDiskCache != null){
				this.mDiskCache.remove(cacheKey);
			}
		}
		catch (IOException localIOException)
		{
		}
	}

	public void setBitmapType(String bitmapType)
	{
		this.bitmapType = bitmapType;
	}

	/**初始化磁盘缓存*/
	private class InitDiskCacheTask extends AsyncTask<File, Void, Void>
	{
		private InitDiskCacheTask()
		{
		}
		
		@Override
		protected Void doInBackground(File... params)
		{
			synchronized (mDiskCacheLock)
			{
				try
				{
					File cacheDir = params[0];
					if (!cacheDir.exists())
						cacheDir.mkdirs();
					mDiskCache = DiskLruCache.open(cacheDir, getAppVersion(mContext), 1, DISK_MAX_SIZE);
					mDiskCacheStarting = false;
					mDiskCacheLock.notifyAll();
				}
				catch (IOException localIOException)
				{
					Log.e(TAG, localIOException.getMessage());
				}
			}
			return null;
		}
	}

	/**获取磁盘缓存中的大小，单位是*/
	public String getSize(){
		long diskSizeByte = mDiskCache.size();//返回当前缓存路径下所有缓存数据的总字节数，以byte为单位

		return FormetFileSize(diskSizeByte);
	}

	/**
	 * 转换文件大小
	 * @param fileSize
	 * @return
	 */
	private String FormetFileSize(long fileSize)
	{
		DecimalFormat df = new DecimalFormat("#.00");
		String fileSizeString = "";
		String wrongSize="0B";
		if(fileSize==0){
			return wrongSize;
		}
		if (fileSize < 1024){
			fileSizeString = df.format((double) fileSize) + "B";
		}
		else if (fileSize < 1048576){
			fileSizeString = df.format((double) fileSize / 1024) + "KB";
		}
		else if (fileSize < 1073741824){
			fileSizeString = df.format((double) fileSize / 1048576) + "MB";
		}
		else{
			fileSizeString = df.format((double) fileSize / 1073741824) + "GB";
		}
		return fileSizeString;
	}
}
