/*
 * Copyright (C) 2007-2009 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader;

import java.util.List;
import java.io.*;
import java.net.*;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.net.Uri;
import android.content.Intent;

import org.geometerplus.zlibrary.core.dialogs.ZLDialogManager;
//import org.geometerplus.zlibrary.ui.android.dialogs.ZLAndroidDialogManager;

import org.geometerplus.zlibrary.ui.android.R;

public class BookDownloader extends Activity {
	private static ProgressBar ourProgressBar;
	private static int ourFileLength = -1;
	private static int ourDownloadedPart = 0;
	private static String ourFileName = "";

	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		//((ZLAndroidDialogManager)ZLDialogManager.getInstance()).setActivity(this);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.downloader);
		ourProgressBar = (ProgressBar)findViewById(android.R.id.progress);

		final Intent intent = getIntent();
		final Uri uri = intent.getData();
		if (uri != null) {
			intent.setData(null);

			final List<String> path = uri.getPathSegments();
			ourFileName = path.get(path.size() - 1);
			if (!ourFileName.endsWith(".fb2.zip") &&
				!ourFileName.endsWith(".fb2") &&
				!ourFileName.endsWith(".epub")) {
				startNextMatchingActivity(intent);
				finish();
				return;
			}

			String host = uri.getHost();
			if (host.equals("www.feedbooks.com")) {
				host = "feedbooks.com";
			}
			String dir = "/sdcard/Books/" + host;
			for (int i = 0; i < path.size() - 1; ++i) {
				dir += '/' + path.get(i);
			}
			final File dirFile = new File(dir);
			dirFile.mkdirs();
			if (!dirFile.isDirectory()) {
				// TODO: error message
				finish();
				return;
			}

			final File fileFile = new File(dirFile, ourFileName);
			if (fileFile.exists()) {
				if (!fileFile.isFile()) {
					// TODO: error message
					finish();
					return;
				}
				// TODO: question box: redownload?
				/*
				ZLDialogManager.getInstance().showQuestionBox(
					"redownloadBox", "Redownload?",
					"no", null,
					"yes", null,
					null, null
				);
				*/
				runFBReader(fileFile);
				return;
			}
			startFileDownload(uri.toString(), fileFile);
		}

		if (ourFileLength <= 0) {
			ourProgressBar.setIndeterminate(true);
		} else {
			ourProgressBar.setIndeterminate(false);
			ourProgressBar.setMax(ourFileLength);
			ourProgressBar.setProgress(ourDownloadedPart);
		}

		final TextView textView = (TextView)findViewById(R.id.downloadertext);
		textView.setText(ZLDialogManager.getWaitMessageText("downloadingFile").replace("%s", ourFileName));
	}

	private void runFBReader(final File file) {
		finish();
		final Intent intent = new Intent(this, FBReader.class);
		intent.setData(Uri.fromFile(file));
		startActivity(intent);
	}

	private void startFileDownload(final String uriString, final File file) {
		final Handler handler = new Handler() {
			public void handleMessage(Message message) {
				try {
					runFBReader(file);
				} catch (Exception e) {
				}
			}
		};
		new Thread(new Runnable() {
			public void run() {
				try {
					final URL url = new URL(uriString);
					final URLConnection connection = url.openConnection();
					ourFileLength = connection.getContentLength();
					if (ourFileLength > 0) {
						ourProgressBar.setIndeterminate(false);
						ourProgressBar.setMax(ourFileLength);
						ourProgressBar.setProgress(0);
						ourDownloadedPart = 0;
					}
					final HttpURLConnection httpConnection = (HttpURLConnection)connection;
					final int response = httpConnection.getResponseCode();
					if (response == HttpURLConnection.HTTP_OK) {
						InputStream inStream = httpConnection.getInputStream();
						OutputStream outStream = new FileOutputStream(file);
						final byte[] buffer = new byte[8192];
						int fullSize = 0;	
						while (true) {
							final int size = inStream.read(buffer);
							if (size <= 0) {
								break;
							}
							ourDownloadedPart += size;
							ourProgressBar.setProgress(ourDownloadedPart);
							outStream.write(buffer, 0, size);
						}
						inStream.close();
						outStream.close();
					}
				} catch (MalformedURLException e) {
					// TODO: error message; remove file, don't start FBReader
				} catch (IOException e) {
					// TODO: error message; remove file, don't start FBReader
				}
				handler.sendEmptyMessage(0);
				ourFileLength = -1;
			}
		}).start();
	}
}