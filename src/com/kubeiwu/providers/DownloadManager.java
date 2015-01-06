/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kubeiwu.providers;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.BaseColumns;
import android.util.Pair;

import com.kubeiwu.providers.downloads.Downloads;

/**
 * The download manager is a system service that handles long-running HTTP downloads. Clients may request that a URI be downloaded to a particular destination file. The download manager will conduct the download in the background, taking care of HTTP interactions and retrying downloads after failures or across connectivity changes and system reboots.
 * 
 * Instances of this class should be obtained through {@link android.content.Context#getSystemService(String)} by passing {@link android.content.Context#DOWNLOAD_SERVICE}.
 * 
 * Apps that request downloads through this API should register a broadcast receiver for {@link #ACTION_NOTIFICATION_CLICKED} to appropriately handle when the user clicks on a running download in a notification or from the downloads UI.
 */
public class DownloadManager {
	@SuppressWarnings("unused")
	private static final String TAG = "DownloadManager";

	/**
	 * 主键 An identifier for a particular download, unique across the system. Clients use this ID to make subsequent calls related to the download.
	 */
	public final static String COLUMN_ID = BaseColumns._ID;// 主键

	/**
	 * 标题 The client-supplied title for this download. This will be displayed in system notifications. Defaults to the empty string.
	 */
	public final static String COLUMN_TITLE = "title";// 标题

	/**
	 * 描叙 The client-supplied description of this download. This will be displayed in system notifications. Defaults to the empty string.
	 */
	public final static String COLUMN_DESCRIPTION = "description";// 描叙

	/**
	 * 下载地址 URI to be downloaded.
	 */
	public final static String COLUMN_URI = "uri";// 下载地址

	/**
	 * 下载文件类型 Internet Media Type of the downloaded file. If no value is provided upon creation, this will initially be null and will be filled in based on the server's response once the download has started.
	 * 
	 * @see <a href="http://www.ietf.org/rfc/rfc1590.txt">RFC 1590, defining Media Types</a>
	 */
	public final static String COLUMN_MEDIA_TYPE = "media_type";// 下载文件类型

	/**
	 * 总大小 Total size of the download in bytes. This will initially be -1 and will be filled in once the download starts.
	 */
	public final static String COLUMN_TOTAL_SIZE_BYTES = "total_size";// 总大小

	/**
	 * 本地地址 Uri where downloaded file will be stored. If a destination is supplied by client, that URI will be used here. Otherwise, the value will initially be null and will be filled in with a generated URI once the download has started.
	 */
	public final static String COLUMN_LOCAL_URI = "local_uri";// 本地地址

	/**
	 * 状态 Current status of the download, as one of the STATUS_* constants.
	 */
	public final static String COLUMN_STATUS = "status";// 状态

	/**
	 * 原因 Provides more detail on the status of the download. Its meaning depends on the value of {@link #COLUMN_STATUS}.
	 * 
	 * When {@link #COLUMN_STATUS} is {@link #STATUS_FAILED}, this indicates the type of error that occurred. If an HTTP error occurred, this will hold the HTTP status code as defined in RFC 2616. Otherwise, it will hold one of the ERROR_* constants.
	 * 
	 * When {@link #COLUMN_STATUS} is {@link #STATUS_PAUSED}, this indicates why the download is paused. It will hold one of the PAUSED_* constants.
	 * 
	 * If {@link #COLUMN_STATUS} is neither {@link #STATUS_FAILED} nor {@link #STATUS_PAUSED}, this column's value is undefined.
	 * 
	 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec6.html#sec6.1.1">RFC 2616 status codes</a>
	 */
	public final static String COLUMN_REASON = "reason";// 原因

	/**
	 * 到目前为止下载了多少 Number of bytes download so far.
	 */
	public final static String COLUMN_BYTES_DOWNLOADED_SO_FAR = "bytes_so_far";// 到目前为止下载了多少

	/**
	 * Timestamp when the download was last modified, in {@link System#currentTimeMillis System.currentTimeMillis()} (wall clock time in UTC).
	 */
	public final static String COLUMN_LAST_MODIFIED_TIMESTAMP = "last_modified_timestamp";

	/**
	 * 等待中.. Value of {@link #COLUMN_STATUS} when the download is waiting to start.
	 */
	public final static int STATUS_PENDING = 1 << 0;// 等待中..

	/**
	 * 正在下载 Value of {@link #COLUMN_STATUS} when the download is currently running.
	 */
	public final static int STATUS_RUNNING = 1 << 1;// 正在下载

	/**
	 * 暂停的 Value of {@link #COLUMN_STATUS} when the download is waiting to retry or resume.
	 */
	public final static int STATUS_PAUSED = 1 << 2;// 暂停的

	/**
	 * 下载完成的 Value of {@link #COLUMN_STATUS} when the download has successfully completed.
	 */
	public final static int STATUS_SUCCESSFUL = 1 << 3;// 下载完成的
	/**
	 * 下载失败 Value of {@link #COLUMN_STATUS} when the download has failed (and will not be retried).
	 */
	public final static int STATUS_FAILED = 1 << 4;// 下载失败

	/**
	 * 未知错误 Value of COLUMN_ERROR_CODE when the download has completed with an error that doesn't fit under any other error code.
	 */
	public final static int ERROR_UNKNOWN = 1000;// 未知错误

	/**
	 * 文件错误 Value of {@link #COLUMN_REASON} when a storage issue arises which doesn't fit under any other error code. Use the more specific {@link #ERROR_INSUFFICIENT_SPACE} and {@link #ERROR_DEVICE_NOT_FOUND} when appropriate.
	 */
	public final static int ERROR_FILE_ERROR = 1001;// 文件错误

	/**
	 * http code错误 Value of {@link #COLUMN_REASON} when an HTTP code was received that download manager can't handle.
	 */
	public final static int ERROR_UNHANDLED_HTTP_CODE = 1002;// http code错误

	/**
	 * 此下载不会因为一个错误接收或处理数据在HTTP级别完成。 Value of {@link #COLUMN_REASON} when an error receiving or processing data occurred at the HTTP level.
	 */
	public final static int ERROR_HTTP_DATA_ERROR = 1004;// 此下载不会因为一个错误接收或处理数据在HTTP级别完成。

	/**
	 * 此下载不能因为有太多的重定向完成。 Value of {@link #COLUMN_REASON} when there were too many redirects.
	 */
	public final static int ERROR_TOO_MANY_REDIRECTS = 1005;// 此下载不能因为有太多的重定向完成。

	/**
	 * 此下载不能由于存储空间不足，完成。通常，这是因为SD卡已满。 Value of {@link #COLUMN_REASON} when there was insufficient storage space. Typically, this is because the SD card is full.
	 */
	public final static int ERROR_INSUFFICIENT_SPACE = 1006;// 此下载不能由于存储空间不足，完成。通常，这是因为SD卡已满。

	/**
	 * Value of {@link #COLUMN_REASON} when no external storage device was found. Typically, this is because the SD card is not mounted.
	 */
	public final static int ERROR_DEVICE_NOT_FOUND = 1007;

	/**
	 * Value of {@link #COLUMN_REASON} when some possibly transient error occurred but we can't resume the download.
	 */
	public final static int ERROR_CANNOT_RESUME = 1008;

	/**
	 * Value of {@link #COLUMN_REASON} when the requested destination file already exists (the download manager will not overwrite an existing file).
	 */
	public final static int ERROR_FILE_ALREADY_EXISTS = 1009;

	/**
	 * 等待重试 Value of {@link #COLUMN_REASON} when the download is paused because some network error occurred and the download manager is waiting before retrying the request.
	 */
	public final static int PAUSED_WAITING_TO_RETRY = 1;// 等待重试

	/**
	 * 网络问题等待 Value of {@link #COLUMN_REASON} when the download is waiting for network connectivity to proceed.
	 */
	public final static int PAUSED_WAITING_FOR_NETWORK = 2;// 网络问题等待

	/**
	 * Value of {@link #COLUMN_REASON} when the download exceeds a size limit for downloads over the mobile network and the download manager is waiting for a Wi-Fi connection to proceed.
	 */
	public final static int PAUSED_QUEUED_FOR_WIFI = 3;

	/**
	 * Value of {@link #COLUMN_REASON} when the download is paused for some other reason.
	 */
	public final static int PAUSED_UNKNOWN = 4;

	/**
	 * 下载完成后发送的广播的action Broadcast intent action sent by the download manager when a download completes.
	 */
	public final static String ACTION_DOWNLOAD_COMPLETE = "android.intent.action.DOWNLOAD_COMPLETE";

	/**
	 * 通知被点击后发送的广播的action Broadcast intent action sent by the download manager when the user clicks on a running download, either from a system notification or from the downloads UI.
	 */
	public final static String ACTION_NOTIFICATION_CLICKED = "android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED";

	/**
	 * 显示所有的下载的action Intent action to launch an activity to display all downloads.
	 */
	public final static String ACTION_VIEW_DOWNLOADS = "android.intent.action.VIEW_DOWNLOADS";

	/**
	 * 下载任务id的别名 Intent extra included with {@link #ACTION_DOWNLOAD_COMPLETE} intents, indicating the ID (as a long) of the download that just completed.
	 */
	public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";

	// this array must contain all public columns
	/**
	 * 这里必须包含所有公共的字段(给cursord的包装类用的)
	 */
	private static final String[] COLUMNS = //
	new String[] { COLUMN_ID, // 1
			COLUMN_TITLE, // 2
			COLUMN_DESCRIPTION, // 3
			COLUMN_URI, // 4
			COLUMN_MEDIA_TYPE, // 5
			COLUMN_TOTAL_SIZE_BYTES, // 6------------
			COLUMN_LOCAL_URI, // 7
			COLUMN_STATUS, // 8
			COLUMN_REASON,// 9
			COLUMN_BYTES_DOWNLOADED_SO_FAR,// 10
			COLUMN_LAST_MODIFIED_TIMESTAMP // 11
	};

	// columns to request from DownloadProvider
	private static final String[] UNDERLYING_COLUMNS = //
	new String[] { Downloads._ID,// 1
			Downloads.COLUMN_TITLE, // 2
			Downloads.COLUMN_DESCRIPTION, // 3
			Downloads.COLUMN_URI,// 4
			Downloads.COLUMN_MIME_TYPE,// 5
			Downloads.COLUMN_TOTAL_BYTES, // 6-------------
			Downloads.COLUMN_STATUS, // 7
			Downloads.COLUMN_CURRENT_BYTES, // 8
			Downloads.COLUMN_LAST_MODIFICATION, // 9
			Downloads.COLUMN_DESTINATION, // 10
			Downloads.COLUMN_FILE_NAME_HINT, // 11
			Downloads._DATA, // 12
	};

	private static final Set<String> LONG_COLUMNS = //
	new HashSet<String>(Arrays.asList(//
			COLUMN_ID, // 1
			COLUMN_TOTAL_SIZE_BYTES, // 2
			COLUMN_STATUS, // 3
			COLUMN_REASON,// 4
			COLUMN_BYTES_DOWNLOADED_SO_FAR, // 5
			COLUMN_LAST_MODIFIED_TIMESTAMP// 6
			));

	/**
	 * This class contains all the information necessary to request a new download. The URI is the only required parameter.
	 * 
	 * Note that the default download destination is a shared volume where the system might delete your file if it needs to reclaim space for system use. If this is a problem, use a location on external storage (see {@link #setDestinationUri(Uri)}.
	 */
	public static class Request {
		/**
		 * Bit flag for {@link #setAllowedNetworkTypes} corresponding to {@link ConnectivityManager#TYPE_MOBILE}.
		 */
		public static final int NETWORK_MOBILE = 1 << 0;

		/**
		 * Bit flag for {@link #setAllowedNetworkTypes} corresponding to {@link ConnectivityManager#TYPE_WIFI}.
		 */
		public static final int NETWORK_WIFI = 1 << 1;

		private Uri mUri;// 下载地址
		private Uri mDestinationUri;// 保存的目标uri
		private List<Pair<String, String>> mRequestHeaders = new ArrayList<Pair<String, String>>();// 请求头信息
		private CharSequence mTitle;// 标题
		private CharSequence mDescription;// 描叙
		private boolean mShowNotification = true;// 是否显示通知
		private String mMimeType;// Mime类型 (图片 文件 ===)
		private boolean mRoamingAllowed = true;// 允许漫游。？？
		private int mAllowedNetworkTypes = ~0; // default to all network
												// types允许下载的网络类型（默认都全部都可以）
		// allowed
		private boolean mIsVisibleInDownloadsUi = true;// 是否显示下载界面？？？

		/**
		 * 必须包含一个可以下载的下载uri
		 * 
		 * @param uri
		 *            the HTTP URI to download.
		 */
		public Request(Uri uri) {
			if (uri == null) {
				throw new NullPointerException();
			}
			String scheme = uri.getScheme();
			if (scheme == null || !scheme.equals("http")) {
				throw new IllegalArgumentException("Can only download HTTP URIs: " + uri);
			}
			mUri = uri;
		}

		/**
		 * 设置要保存到的Uri Set the local destination for the downloaded file. Must be a file URI to a path on external storage, and the calling application must have the WRITE_EXTERNAL_STORAGE permission.
		 * 
		 * If the URI is a directory(ending with "/"), destination filename will be generated.
		 * 
		 * @return this object
		 */
		public Request setDestinationUri(Uri uri) {
			mDestinationUri = uri;
			return this;
		}

		/**
		 * 设置保存到sd的位置 Set the local destination for the downloaded file to a path within the application's external files directory (as returned by {@link Context#getExternalFilesDir(String)}.
		 * 
		 * @param context
		 *            the {@link Context} to use in determining the external files directory
		 * @param dirType
		 *            the directory type to pass to {@link Context#getExternalFilesDir(String)}
		 * @param subPath
		 *            the path within the external directory. If subPath is a directory(ending with "/"), destination filename will be generated.
		 * @return this object
		 */
		public Request setDestinationInExternalFilesDir(Context context, String dirType, String subPath) {
			// 下载的文件保存到sd卡
			setDestinationFromBase(context.getExternalFilesDir(dirType), subPath);
			return this;
		}

		/**
		 * 设置保存到sd的位置 Set the local destination for the downloaded file to a path within the public external storage directory (as returned by {@link Environment#getExternalStoragePublicDirectory(String)}.
		 * 
		 * @param dirType
		 *            the directory type to pass to {@link Environment#getExternalStoragePublicDirectory(String)}
		 * @param subPath
		 *            the path within the external directory. If subPath is a directory(ending with "/"), destination filename will be generated.
		 * @return this object
		 */
		public Request setDestinationInExternalPublicDir(String dirType, String subPath) {
			// 公共的下载文件夹Environment.getExternalStoragePublicDirectory(dirType
			setDestinationFromBase(Environment.getExternalStoragePublicDirectory(dirType), subPath);
			return this;
		}

		private void setDestinationFromBase(File base, String subPath) {
			if (subPath == null) {
				throw new NullPointerException("subPath cannot be null");
			}
			mDestinationUri = Uri.withAppendedPath(Uri.fromFile(base), subPath);
		}

		/**
		 * 增加请求头到mRequestHeaders Add an HTTP header to be included with the download request. The header will be added to the end of the list.
		 * 
		 * @param header
		 *            HTTP header name
		 * @param value
		 *            header value
		 * @return this object
		 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">HTTP/1.1 Message Headers</a>
		 */
		public Request addRequestHeader(String header, String value) {//
			if (header == null) {
				throw new NullPointerException("header cannot be null");
			}
			if (header.contains(":")) {
				throw new IllegalArgumentException("header may not contain ':'");
			}
			if (value == null) {
				value = "";
			}
			mRequestHeaders.add(Pair.create(header, value));
			return this;
		}

		/**
		 * 设置标题 Set the title of this download, to be displayed in notifications (if enabled). If no title is given, a default one will be assigned based on the download filename, once the download starts.
		 * 
		 * @return this object
		 */
		public Request setTitle(CharSequence title) {
			mTitle = title;
			return this;
		}

		/**
		 * 设置描叙信息 Set a description of this download, to be displayed in notifications (if enabled)
		 * 
		 * @return this object
		 */
		public Request setDescription(CharSequence description) {
			mDescription = description;
			return this;
		}

		/**
		 * 设置MIME Set the MIME content type of this download. This will override the content type declared in the server's response.
		 * 
		 * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.7">HTTP/1.1 Media Types</a>
		 * @return this object
		 */
		public Request setMimeType(String mimeType) {
			mMimeType = mimeType;
			return this;
		}

		/**
		 * 设置是否显示通知 Control whether a system notification is posted by the download manager while this download is running. If enabled, the download manager posts notifications about downloads through the system {@link com.mozillaonline.providers.NotificationManager}. By default, a notification is shown.
		 * 
		 * If set to false, this requires the permission android.permission.DOWNLOAD_WITHOUT_NOTIFICATION.
		 * 
		 * @param show
		 *            whether the download manager should show a notification for this download.
		 * @return this object
		 */
		public Request setShowRunningNotification(boolean show) {
			mShowNotification = show;
			return this;
		}

		/**
		 * 设置允许下载的网络类型 Restrict the types of networks over which this download may proceed. By default, all network types are allowed.
		 * 
		 * @param flags
		 *            any combination of the NETWORK_* bit flags.
		 * @return this object
		 */
		public Request setAllowedNetworkTypes(int flags) {
			mAllowedNetworkTypes = flags;
			return this;
		}

		/**
		 * 设置是否运行漫游 Set whether this download may proceed over a roaming connection. By default, roaming is allowed.
		 * 
		 * @param allowed
		 *            whether to allow a roaming connection to be used
		 * @return this object
		 */
		public Request setAllowedOverRoaming(boolean allowed) {
			mRoamingAllowed = allowed;
			return this;
		}

		/**
		 * 设置是否可以显示url Set whether this download should be displayed in the system's Downloads UI. True by default.
		 * 
		 * @param isVisible
		 *            whether to display this download in the Downloads UI
		 * @return this object
		 */
		public Request setVisibleInDownloadsUi(boolean isVisible) {
			mIsVisibleInDownloadsUi = isVisible;
			return this;
		}

		/**
		 * 将传人的Request的参数封装为ContentValues,
		 * 
		 * @return ContentValues to be passed to DownloadProvider.insert()
		 */
		ContentValues toContentValues(String packageName) {//
			ContentValues values = new ContentValues();
			assert mUri != null;
			values.put(Downloads.COLUMN_URI, mUri.toString());// 下载地址
			values.put(Downloads.COLUMN_IS_PUBLIC_API, true);
			values.put(Downloads.COLUMN_NOTIFICATION_PACKAGE, packageName);// 通知时候的包名

			if (mDestinationUri != null) {
				values.put(Downloads.COLUMN_DESTINATION, Downloads.DESTINATION_FILE_URI);// 这个需要权限
				values.put(Downloads.COLUMN_FILE_NAME_HINT, mDestinationUri.toString());
			} else {
				values.put(Downloads.COLUMN_DESTINATION, Downloads.DESTINATION_EXTERNAL);
			}

			if (!mRequestHeaders.isEmpty()) {
				encodeHttpHeaders(values);
			}

			putIfNonNull(values, Downloads.COLUMN_TITLE, mTitle);// 添加title
			putIfNonNull(values, Downloads.COLUMN_DESCRIPTION, mDescription);// 添加描叙信息
			putIfNonNull(values, Downloads.COLUMN_MIME_TYPE, mMimeType);// 添加类型

			values.put(Downloads.COLUMN_VISIBILITY, mShowNotification ? Downloads.VISIBILITY_VISIBLE : Downloads.VISIBILITY_HIDDEN);// 显示通知就是true
																																	// 否则farse

			values.put(Downloads.COLUMN_ALLOWED_NETWORK_TYPES, mAllowedNetworkTypes);// 网络类型
			values.put(Downloads.COLUMN_ALLOW_ROAMING, mRoamingAllowed);// 漫游
			values.put(Downloads.COLUMN_IS_VISIBLE_IN_DOWNLOADS_UI, mIsVisibleInDownloadsUi);// 这是否下载应在系统下载的用户界面显示出来。缺省值为真。
			// 含的标志，指示是否启动的应用程序能够验证下载文件的完整的列的名称。设置该标志后，//
			// 下载管理器执行下载并报告成功甚至在某些情况下它不能保证完成下载//
			// （例如当做一个字节范围的要求没有ETag，或当它不能确定是否下载全部完成）。
			values.put(Downloads.COLUMN_NO_INTEGRITY, 1);

			return values;
		}

		/**
		 * 将请求头的信息封装到ContentValues中
		 * 
		 * @param values
		 */
		private void encodeHttpHeaders(ContentValues values) {
			int index = 0;
			for (Pair<String, String> header : mRequestHeaders) {
				String headerString = header.first + ": " + header.second;
				values.put(Downloads.RequestHeaders.INSERT_KEY_PREFIX + index, headerString);
				index++;
			}
		}

		/**
		 * 如果不为null 就加入
		 * 
		 * @param contentValues
		 * @param key
		 * @param value
		 */
		private void putIfNonNull(ContentValues contentValues, String key, Object value) {
			if (value != null) {
				contentValues.put(key, value.toString());
			}
		}
	}

	/**
	 * This class may be used to filter download manager queries.
	 */
	public static class Query {
		/**
		 * Constant for use with {@link #orderBy}
		 * 
		 * @hide
		 */
		public static final int ORDER_ASCENDING = 1;

		/**
		 * Constant for use with {@link #orderBy}
		 * 
		 * @hide
		 */
		public static final int ORDER_DESCENDING = 2;

		private long[] mIds = null;
		private Integer mStatusFlags = null;
		private String mOrderByColumn = Downloads.COLUMN_LAST_MODIFICATION;
		private int mOrderDirection = ORDER_DESCENDING;
		private boolean mOnlyIncludeVisibleInDownloadsUi = false;

		/**
		 * 查询的时候可以指定id Include only the downloads with the given IDs.
		 * 
		 * @return this object
		 */
		public Query setFilterById(long... ids) {
			mIds = ids;
			return this;
		}

		/**
		 * 指定状态查询 Include only downloads with status matching any the given status flags.
		 * 
		 * @param flags
		 *            any combination of the STATUS_* bit flags
		 * @return this object
		 */
		public Query setFilterByStatus(int flags) {
			mStatusFlags = flags;
			return this;
		}

		/**
		 * Controls whether this query includes downloads not visible in the system's Downloads UI.
		 * 
		 * @param value
		 *            if true, this query will only include downloads that should be displayed in the system's Downloads UI; if false (the default), this query will include both visible and invisible downloads.
		 * @return this object
		 * @hide
		 */
		public Query setOnlyIncludeVisibleInDownloadsUi(boolean value) {
			mOnlyIncludeVisibleInDownloadsUi = value;
			return this;
		}

		/**
		 * 排序 ,只能以ORDER_ASCENDING或者ORDER_DESCENDING Change the sort order of the returned Cursor.
		 * 
		 * @param column
		 *            one of the COLUMN_* constants; currently, only {@link #COLUMN_LAST_MODIFIED_TIMESTAMP} and {@link #COLUMN_TOTAL_SIZE_BYTES} are supported.
		 * @param direction
		 *            either {@link #ORDER_ASCENDING} or {@link #ORDER_DESCENDING}
		 * @return this object
		 * @hide
		 */
		public Query orderBy(String column, int direction) {
			if (direction != ORDER_ASCENDING && direction != ORDER_DESCENDING) {
				throw new IllegalArgumentException("Invalid direction: " + direction);
			}

			if (column.equals(COLUMN_LAST_MODIFIED_TIMESTAMP)) {
				mOrderByColumn = Downloads.COLUMN_LAST_MODIFICATION;
			} else if (column.equals(COLUMN_TOTAL_SIZE_BYTES)) {
				mOrderByColumn = Downloads.COLUMN_TOTAL_BYTES;
			} else {
				throw new IllegalArgumentException("Cannot order by " + column);
			}
			mOrderDirection = direction;
			return this;
		}

		/**
		 * 从内容提供者中获取cursor Run this query using the given ContentResolver.
		 * 
		 * @param projection
		 *            the projection to pass to ContentResolver.query()
		 * @return the Cursor returned by ContentResolver.query()
		 */
		Cursor runQuery(ContentResolver resolver, String[] projection, Uri baseUri) {
			Uri uri = baseUri;
			List<String> selectionParts = new ArrayList<String>();
			String[] selectionArgs = null;

			if (mIds != null) {// ids过滤器
				selectionParts.add(getWhereClauseForIds(mIds));// //( OR _id = ?
																// Or _id =?)
				selectionArgs = getWhereArgsForIds(mIds);// id是转为String[] 返回
			}

			if (mStatusFlags != null) {
				List<String> parts = new ArrayList<String>();
				if ((mStatusFlags & STATUS_PENDING) != 0) {
					parts.add(statusClause("=", Downloads.STATUS_PENDING));
				}
				if ((mStatusFlags & STATUS_RUNNING) != 0) {
					parts.add(statusClause("=", Downloads.STATUS_RUNNING));
				}
				if ((mStatusFlags & STATUS_PAUSED) != 0) {
					parts.add(statusClause("=", Downloads.STATUS_PAUSED_BY_APP));
					parts.add(statusClause("=", Downloads.STATUS_WAITING_TO_RETRY));
					parts.add(statusClause("=", Downloads.STATUS_WAITING_FOR_NETWORK));
					parts.add(statusClause("=", Downloads.STATUS_QUEUED_FOR_WIFI));
				}
				if ((mStatusFlags & STATUS_SUCCESSFUL) != 0) {
					parts.add(statusClause("=", Downloads.STATUS_SUCCESS));
				}
				if ((mStatusFlags & STATUS_FAILED) != 0) {
					parts.add("(" + statusClause(">=", 400) + " AND " + statusClause("<", 600) + ")");
				}
				selectionParts.add(joinStrings(" OR ", parts));
			}

			if (mOnlyIncludeVisibleInDownloadsUi) {
				selectionParts.add(Downloads.COLUMN_IS_VISIBLE_IN_DOWNLOADS_UI + " != '0'");
			}

			// only return rows which are not marked 'deleted = 1'
			selectionParts.add(Downloads.COLUMN_DELETED + " != '1'");

			String selection = joinStrings(" AND ", selectionParts);
			String orderDirection = (mOrderDirection == ORDER_ASCENDING ? "ASC" : "DESC");
			String orderBy = mOrderByColumn + " " + orderDirection;

			return resolver.query(uri, projection, selection, selectionArgs, orderBy);
		}

		private String joinStrings(String joiner, Iterable<String> parts) {
			StringBuilder builder = new StringBuilder();
			boolean first = true;
			for (String part : parts) {
				if (!first) {
					builder.append(joiner);
				}
				builder.append(part);
				first = false;
			}
			return builder.toString();
		}

		private String statusClause(String operator, int value) {
			return Downloads.COLUMN_STATUS + operator + "'" + value + "'";
		}
	}

	private ContentResolver mResolver;// 用来操作内容提供者的
	private String mPackageName;// 包名
	private Uri mBaseUri = Downloads.CONTENT_URI;// 默认的uri

	/**
	 * @hide
	 */
	public DownloadManager(ContentResolver resolver, String packageName) {
		mResolver = resolver;
		mPackageName = packageName;
	}


	/**
	 * 使这个对象访问下载商通过/而不是/ my_downloads all_downloads URI的URI，这允许这样做的客户。 Makes this object access the download provider through /all_downloads URIs rather than /my_downloads URIs, for clients that have permission to do so.
	 * 
	 * @hide
	 */
	public void setAccessAllDownloads(boolean accessAllDownloads) {
		if (accessAllDownloads) {
			mBaseUri = Downloads.ALL_DOWNLOADS_CONTENT_URI;
		} else {
			mBaseUri = Downloads.CONTENT_URI;
		}
	}

	/**
	 * 将Request传人到Downloadmanager中来，然后request会被转会为ContentValues，然后插入到内容提供者中， 返回的是下载数据库中的Id主键 插入有会触发下载 Enqueue a new download. The download will start automatically once the download manager is ready to execute it and connectivity is available.
	 * 
	 * @param request
	 *            the parameters specifying this download
	 * @return an ID for the download, unique across the system. This ID is used to make future calls related to this download.
	 */
	public long enqueue(Request request) {
		ContentValues values = request.toContentValues(mPackageName);
		Uri downloadUri = mResolver.insert(Downloads.CONTENT_URI, values);// values插入到内容提供者中
		long id = Long.parseLong(downloadUri.getLastPathSegment());// 返回id
		return id;
	}

	/**
	 * Marks the specified download as 'to be deleted'. This is done when a completed download is to be removed but the row was stored without enough info to delete the corresponding metadata from Mediaprovider database. Actual cleanup of this row is done in DownloadService.
	 * 
	 * @param ids
	 *            the IDs of the downloads to be marked 'deleted'
	 * @return the number of downloads actually updated
	 * @hide
	 */
	public int markRowDeleted(long... ids) {
		if (ids == null || ids.length == 0) {
			// called with nothing to remove!
			throw new IllegalArgumentException("input param 'ids' can't be null");
		}
		ContentValues values = new ContentValues();
		values.put(Downloads.COLUMN_DELETED, 1);
		return mResolver.update(mBaseUri, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
	}

	/**
	 * 删除一个下载 Cancel downloads and remove them from the download manager. Each download will be stopped if it was running, and it will no longer be accessible through the download manager. If a file was already downloaded to external storage, it will not be deleted.
	 * 
	 * @param ids
	 *            the IDs of the downloads to remove
	 * @return the number of downloads actually removed
	 */
	public int remove(long... ids) {
		if (ids == null || ids.length == 0) {
			// called with nothing to remove!
			throw new IllegalArgumentException("input param 'ids' can't be null");
		}
		return mResolver.delete(mBaseUri, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
	}

	/**
	 * 根据query得到cursor Query the download manager about downloads that have been requested.
	 * 
	 * @param query
	 *            parameters specifying filters for this query
	 * @return a Cursor over the result set of downloads, with columns consisting of all the COLUMN_* constants.
	 */
	public Cursor query(Query query) {
		Cursor underlyingCursor = query.runQuery(mResolver, UNDERLYING_COLUMNS, mBaseUri);
		if (underlyingCursor == null) {
			return null;
		}
		return new CursorTranslator(underlyingCursor, mBaseUri);// 进行包装后返回
	}

	/**
	 * Open a downloaded file for reading. The download must have completed.
	 * 
	 * @param id
	 *            the ID of the download
	 * @return a read-only {@link ParcelFileDescriptor}
	 * @throws FileNotFoundException
	 *             if the destination file does not already exist
	 */
	public ParcelFileDescriptor openDownloadedFile(long id) throws FileNotFoundException {
		return mResolver.openFileDescriptor(getDownloadUri(id), "r");
	}

	/**
	 * 暂停 下载 Pause the given downloads, which must be running. This method will only work when called from within the download manager's process.
	 * 
	 * @param ids
	 *            the IDs of the downloads
	 * @hide
	 */
	public void pauseDownload(long... ids) {
		Cursor cursor = query(new Query().setFilterById(ids));
		try {
			for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
				int status = cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS));
				if (status != STATUS_RUNNING && status != STATUS_PENDING) {
					throw new IllegalArgumentException("Can only pause a running download: " + cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
				}
			}
		} finally {
			cursor.close();
		}

		ContentValues values = new ContentValues();
		values.put(Downloads.COLUMN_CONTROL, Downloads.CONTROL_PAUSED);
		values.put(Downloads.COLUMN_NO_INTEGRITY, 1);
		mResolver.update(mBaseUri, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
	}

	/**
	 * 继续下载 Resume the given downloads, which must be paused. This method will only work when called from within the download manager's process.
	 * 
	 * @param ids
	 *            the IDs of the downloads
	 * @hide
	 */
	public void resumeDownload(long... ids) {
		Cursor cursor = query(new Query().setFilterById(ids));
		try {
			for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
				int status = cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS));
				if (status != STATUS_PAUSED) {
					throw new IllegalArgumentException("Cann only resume a paused download: " + cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
				}
			}
		} finally {
			cursor.close();
		}

		ContentValues values = new ContentValues();
		values.put(Downloads.COLUMN_STATUS, Downloads.STATUS_PENDING);
		values.put(Downloads.COLUMN_CONTROL, Downloads.CONTROL_RUN);
		mResolver.update(mBaseUri, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
	}

	/**
	 * 重新下载 Restart the given downloads, which must have already completed (successfully or not). This method will only work when called from within the download manager's process.
	 * 
	 * @param ids
	 *            the IDs of the downloads
	 * @hide
	 */
	public void restartDownload(long... ids) {
		Cursor cursor = query(new Query().setFilterById(ids));
		try {
			for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
				int status = cursor.getInt(cursor.getColumnIndex(COLUMN_STATUS));
				if (status != STATUS_SUCCESSFUL && status != STATUS_FAILED) {
					throw new IllegalArgumentException("Cannot restart incomplete download: " + cursor.getLong(cursor.getColumnIndex(COLUMN_ID)));
				}
			}
		} finally {
			cursor.close();
		}

		ContentValues values = new ContentValues();
		values.put(Downloads.COLUMN_CURRENT_BYTES, 0);
		values.put(Downloads.COLUMN_TOTAL_BYTES, -1);
		values.putNull(Downloads._DATA);
		values.put(Downloads.COLUMN_STATUS, Downloads.STATUS_PENDING);
		mResolver.update(mBaseUri, values, getWhereClauseForIds(ids), getWhereArgsForIds(ids));
	}

	/**
	 * 根据id获取下载的uri Get the DownloadProvider URI for the download with the given ID.
	 */
	Uri getDownloadUri(long id) {
		return ContentUris.withAppendedId(mBaseUri, id);
	}

	/**
	 * 把数据拼接为字符串个数据库操作的 Get a parameterized SQL WHERE clause to select a bunch of IDs.
	 */
	static String getWhereClauseForIds(long[] ids) {
		StringBuilder whereClause = new StringBuilder();
		whereClause.append("(");// ( OR _id = ? Or _id =?)
		for (int i = 0; i < ids.length; i++) {
			if (i > 0) {
				whereClause.append("OR ");
			}
			whereClause.append(BaseColumns._ID);
			whereClause.append(" = ? ");
		}
		whereClause.append(")");
		return whereClause.toString();
	}

	/**
	 * Long转string Get the selection args for a clause returned by {@link #getWhereClauseForIds(long[])}.
	 */
	static String[] getWhereArgsForIds(long[] ids) {
		String[] whereArgs = new String[ids.length];
		for (int i = 0; i < ids.length; i++) {
			whereArgs[i] = Long.toString(ids[i]);
		}
		return whereArgs;
	}

	/**
	 * 对CursorWrapper再次进行封装，提高效率 This class wraps a cursor returned by DownloadProvider -- the "underlying cursor" -- and presents a different set of columns, those defined in the DownloadManager.COLUMN_* constants. Some columns correspond directly to underlying values while others are computed from underlying data.
	 */
	private static class CursorTranslator extends CursorWrapper {
		public CursorTranslator(Cursor cursor, Uri baseUri) {
			super(cursor);
		}

		/**
		 * 从COLUMNS数组中直接返回位置，不从底层查询，提高效率
		 * 
		 * @return columnName的index
		 */
		@Override
		public int getColumnIndex(String columnName) {
			return Arrays.asList(COLUMNS).indexOf(columnName);
		}

		@Override
		/**
		 * 如果没有找到就拋异常
		 */
		public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
			int index = getColumnIndex(columnName);
			if (index == -1) {
				throw new IllegalArgumentException("No such column: " + columnName);
			}
			return index;
		}

		@Override
		/**
		 * 根据columindex获取名字
		 */
		public String getColumnName(int columnIndex) {
			int numColumns = COLUMNS.length;
			if (columnIndex < 0 || columnIndex >= numColumns) {
				throw new IllegalArgumentException("Invalid column index " + columnIndex + ", " + numColumns + " columns exist");
			}
			return COLUMNS[columnIndex];
		}

		@Override
		/**
		 * 获取所有的ColumnNames
		 */
		public String[] getColumnNames() {
			String[] returnColumns = new String[COLUMNS.length];
			System.arraycopy(COLUMNS, 0, returnColumns, 0, COLUMNS.length);
			return returnColumns;
		}

		@Override
		/**
		 * 获取 Column 的数量
		 */
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		/**
		 * 不允许存放Blob，直接拋异常
		 */
		public byte[] getBlob(int columnIndex) {
			throw new UnsupportedOperationException();
		}

		@Override
		/**
		 * 获取Doubt值（其实获取的是Long）
		 */
		public double getDouble(int columnIndex) {
			return getLong(columnIndex);
		}

		/**
		 * 判断这个column的类型是否是long类型
		 * 
		 * @param column
		 *            要判断的column
		 * @return
		 */
		private boolean isLongColumn(String column) {
			return LONG_COLUMNS.contains(column);
		}

		@Override
		public float getFloat(int columnIndex) {
			return (float) getDouble(columnIndex);
		}

		@Override
		public int getInt(int columnIndex) {
			return (int) getLong(columnIndex);
		}

		@Override
		public long getLong(int columnIndex) {
			return translateLong(getColumnName(columnIndex));
		}

		@Override
		public short getShort(int columnIndex) {
			return (short) getLong(columnIndex);
		}

		@Override
		public String getString(int columnIndex) {
			return translateString(getColumnName(columnIndex));
		}

		/**
		 * column转换为String
		 * 
		 * @param column
		 * @return
		 */
		private String translateString(String column) {
			if (isLongColumn(column)) {
				return Long.toString(translateLong(column));
			}
			if (column.equals(COLUMN_TITLE)) {// title
				return getUnderlyingString(Downloads.COLUMN_TITLE);
			}
			if (column.equals(COLUMN_DESCRIPTION)) {// 描叙
				return getUnderlyingString(Downloads.COLUMN_DESCRIPTION);
			}
			if (column.equals(COLUMN_URI)) {// 下载地址uri
				return getUnderlyingString(Downloads.COLUMN_URI);
			}
			if (column.equals(COLUMN_MEDIA_TYPE)) {// media_type
				return getUnderlyingString(Downloads.COLUMN_MIME_TYPE);
			}

			assert column.equals(COLUMN_LOCAL_URI);
			return getLocalUri();
		}

		private String getLocalUri() {
			String localPath = getUnderlyingString(Downloads._DATA);
			if (localPath == null) {
				return null;
			}
			return Uri.fromFile(new File(localPath)).toString();
		}

		/**
		 * column转换为Long
		 * 
		 * @param column
		 * @return
		 */
		private long translateLong(String column) {
			if (!isLongColumn(column)) {
				// mimic behavior of underlying cursor -- most likely, throw
				// NumberFormatException
				return Long.valueOf(translateString(column));
			}

			if (column.equals(COLUMN_ID)) {
				return getUnderlyingLong(BaseColumns._ID);// 获取id
			}
			if (column.equals(COLUMN_TOTAL_SIZE_BYTES)) {
				return getUnderlyingLong(Downloads.COLUMN_TOTAL_BYTES);// 总大小
			}
			if (column.equals(COLUMN_STATUS)) {
				return translateStatus((int) getUnderlyingLong(Downloads.COLUMN_STATUS));// 状态
			}
			if (column.equals(COLUMN_REASON)) {
				return getReason((int) getUnderlyingLong(Downloads.COLUMN_STATUS));//
			}
			if (column.equals(COLUMN_BYTES_DOWNLOADED_SO_FAR)) {
				return getUnderlyingLong(Downloads.COLUMN_CURRENT_BYTES);
			}
			assert column.equals(COLUMN_LAST_MODIFIED_TIMESTAMP);
			return getUnderlyingLong(Downloads.COLUMN_LAST_MODIFICATION);
		}

		private long getReason(int status) {
			switch (translateStatus(status)) {
			case STATUS_FAILED:
				return getErrorCode(status);

			case STATUS_PAUSED:
				return getPausedReason(status);//

			default:
				return 0; // arbitrary value when status is not an error
			}
		}

		private long getPausedReason(int status) {
			switch (status) {
			case Downloads.STATUS_WAITING_TO_RETRY:
				return PAUSED_WAITING_TO_RETRY;// 暂停等待重试

			case Downloads.STATUS_WAITING_FOR_NETWORK:
				return PAUSED_WAITING_FOR_NETWORK;// 暂停等待网络

			case Downloads.STATUS_QUEUED_FOR_WIFI:
				return PAUSED_QUEUED_FOR_WIFI;// 暂停队列

			default:
				return PAUSED_UNKNOWN;// 暂停 不知道原因
			}
		}

		private long getErrorCode(int status) {
			if ((400 <= status && status < Downloads.MIN_ARTIFICIAL_ERROR_STATUS) || (500 <= status && status < 600)) {
				// HTTP status code
				return status;
			}

			switch (status) {
			case Downloads.STATUS_FILE_ERROR:
				return ERROR_FILE_ERROR;// 文件错误

			case Downloads.STATUS_UNHANDLED_HTTP_CODE:
			case Downloads.STATUS_UNHANDLED_REDIRECT:
				return ERROR_UNHANDLED_HTTP_CODE;// 此下载不能因为不明的未经处理的HTTP代码完成。

			case Downloads.STATUS_HTTP_DATA_ERROR:
				return ERROR_HTTP_DATA_ERROR;// 此下载不会因为一个错误接收或处理数据在HTTP级别完成。

			case Downloads.STATUS_TOO_MANY_REDIRECTS:
				return ERROR_TOO_MANY_REDIRECTS;// 此下载不能因为有太多的重定向完成。

			case Downloads.STATUS_INSUFFICIENT_SPACE_ERROR:// 此下载不能由于存储空间不足，完成。通常，这是因为SD卡已满。
				return ERROR_INSUFFICIENT_SPACE;

			case Downloads.STATUS_DEVICE_NOT_FOUND_ERROR:// 此下载不能因为没有外部存储装置被发现了。通常，这是因为SD卡不安装。
				return ERROR_DEVICE_NOT_FOUND;

			case Downloads.STATUS_CANNOT_RESUME:// 发生了一些可能暂时的错误，但我们不能恢复下载。
				return ERROR_CANNOT_RESUME;

			case Downloads.STATUS_FILE_ALREADY_EXISTS_ERROR:// 所请求的目标文件已存在。
				return ERROR_FILE_ALREADY_EXISTS;

			default:
				return ERROR_UNKNOWN;// 价值column_error_code当下载完成了一个错误，适合任何其他错误代码在不。
			}
		}

		/**
		 * 获取真实的Long值
		 * 
		 * @param column
		 * @return
		 */
		private long getUnderlyingLong(String column) {
			return super.getLong(super.getColumnIndex(column));
		}

		/**
		 * 获取真实的String值
		 * 
		 * @param column
		 * @return
		 */
		private String getUnderlyingString(String column) {
			return super.getString(super.getColumnIndex(column));
		}

		private int translateStatus(int status) {
			switch (status) {
			case Downloads.STATUS_PENDING:// 等待
				return STATUS_PENDING;

			case Downloads.STATUS_RUNNING:// 运行中
				return STATUS_RUNNING;

			case Downloads.STATUS_PAUSED_BY_APP:
			case Downloads.STATUS_WAITING_TO_RETRY:
			case Downloads.STATUS_WAITING_FOR_NETWORK:
			case Downloads.STATUS_QUEUED_FOR_WIFI:
				return STATUS_PAUSED;// 暂停

			case Downloads.STATUS_SUCCESS:
				return STATUS_SUCCESSFUL;// 完成

			default:
				assert Downloads.isStatusError(status);
				return STATUS_FAILED;
			}
		}
	}
}
