package com.bluevia.android.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Path.FillType;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.bluevia.android.rest.commons.BlueviaClientException;
import com.bluevia.android.rest.oauth.client.BlueviaOauthClient;
import com.bluevia.android.rest.oauth.data.RequestToken;
import com.bluevia.android.rest.oauth.data.Token;

public class AuthDialog extends Dialog {
	
	private static final String TAG = AuthDialog.class.getSimpleName();
	private static final String SUCCESS_URL = "https://m.connect.bluevia.com/en/authorise/success/";
	private static final Pattern PIN_PATTERN = Pattern.compile("https://m.connect.bluevia.com/en/authorise/success/%252F(.+)/.+");

	//Error codes
	public enum AUTH_ERROR {
		ABORTED,
		REQUEST_TOKEN_ERROR,
		ACCESS_TOKEN_ERROR,
		DENIED
	}

	
	/**
	 * Defines the interface that will be called
	 * when the auth operation ends (in a good or
	 * bad way)
	 */
	public interface AuthDialogListener {
		public void onError(AUTH_ERROR code);
		
		public void onComplete(Token result);
	}
	
	
	private BlueviaOauthClient mOauth;
	private AuthDialogListener mListener;
	private String mSecret;
	
	private WebView mWebView;
	
	private RequestToken mToken;
	
	public AuthDialog(Context context, 
			String title,
			BlueviaOauthClient oauth,
			AuthDialogListener listener) {
		
		super(context);
		
		this.setTitle(title);
		
		mOauth = oauth;
		mListener = listener;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.auth_dialog);
		
		//Create the webview and configure it		
		mWebView = (WebView) findViewById(R.id.oauth_webview);
		mWebView.setVerticalScrollBarEnabled(false);
        mWebView.setHorizontalScrollBarEnabled(false);
        mWebView.setWebViewClient(new BlueViaWebClient());
        mWebView.getSettings().setJavaScriptEnabled(true);
		
        //Start with the web loading
		new OauthDanceTask().execute(null);
	}
	
	@Override
	public void onBackPressed() {
		super.onBackPressed();
		dismiss();
		mListener.onError(AUTH_ERROR.ABORTED);				
	}
	
	/**
	 *	Get the request token using the BlueVia sdk and
	 *	start the Oauth dance loading the urls in the
	 *	webview
	 */
	private class OauthDanceTask extends AsyncTask<Void, Void, Boolean>{

		@Override
		protected Boolean doInBackground(Void... arg0) {
			try {
				mToken = (RequestToken) mOauth.getRequestToken();
				Log.d(TAG, "Request Token: " + mToken);
			} catch (BlueviaClientException e) {
				return Boolean.FALSE;
			}
			return Boolean.TRUE;
		}
		
		@Override
		protected void onPostExecute(Boolean result) {
			if(!result) {
				mListener.onError(AUTH_ERROR.REQUEST_TOKEN_ERROR);
				return;
			}
			
			findViewById(R.id.loading_layout).setVisibility(View.GONE);
			mWebView.setVisibility(View.VISIBLE);
			Log.d(TAG, "Loading url: " + mToken.getVerificationUrl());
			mWebView.loadUrl(mToken.getVerificationUrl());
		}
		
	}
	
	/**
	 * Async task that will exchange the oauth verifier for the
	 * proper access token
	 *
	 */
	private class OauthGetAccessTokenTask extends AsyncTask<Void, Void, Token> {
		
		@Override
		protected void onPreExecute() {
			mWebView.setVisibility(View.GONE);
			findViewById(R.id.loading_layout).setVisibility(View.VISIBLE);
		}

		@Override
		protected Token doInBackground(Void... arg0) {
			try {
				return mOauth.getAccessToken(mToken.getToken(), mToken.getSecret(), mSecret);
				
			} catch (BlueviaClientException e) {
				return null;
			}
		}
		
		@Override
		protected void onPostExecute(Token accessToken) {
			if(accessToken != null) {
				mListener.onComplete(accessToken);
			} else {
				mListener.onError(AUTH_ERROR.ACCESS_TOKEN_ERROR);
			}
			
			AuthDialog.this.dismiss();
		}
		
	}
	
	/**
	 * Class that extends the webclient to interact with the
	 * Bluevia authentication pages.
	 * 
	 * Will automatically dismiss the dialog when the process
	 * is complete.
	 *
	 */
	private class BlueViaWebClient extends WebViewClient {
		@Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "Loading url: " + url);
			if(url != null && url.startsWith(SUCCESS_URL)) {
				//Extract the information and dismiss
				Matcher matcher = PIN_PATTERN.matcher(url);
				if(matcher != null && matcher.matches()) {
					Log.d(TAG, "Got the authorization!");
					mSecret = matcher.group(1);
					//Exchange the request token for the access token
					new OauthGetAccessTokenTask().execute(null);
				} else {
					mListener.onError(AUTH_ERROR.DENIED);
				}
				AuthDialog.this.dismiss();
				return false;
			}
			mWebView.loadUrl(url);
			return true;
		}
		
		@Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            Log.d(TAG, "WebView loading URL: " + url);
            super.onPageStarted(view, url, favicon);
            
        }
		
		@Override
        public void onPageFinished(WebView view, String url) {
			Log.d(TAG, "WeView finished loading " + url);
            super.onPageFinished(view, url); 
            
            mWebView.requestFocus(View.FOCUS_DOWN);
        } 
	}

}
