/*
 * Copyright (c) 2012, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.util.Locale;

import com.psiphon3.ServerInterface.PsiphonServerInterfaceException;
import com.psiphon3.Utils.MyLog;

import android.app.Activity;
import android.content.Intent;
import android.net.MailTo;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class FeedbackActivity extends Activity
{

    private WebView webView;
    
    public void onCreate(Bundle savedInstanceState)
    {
        final Activity activity = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.feedback);

        webView = (WebView)findViewById(R.id.feedbackWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient()
        {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                final String feedbackUrl = "feedback?";
                
                if (url.startsWith("mailto:"))
                {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("message/rfc822");
                    intent.putExtra(Intent.EXTRA_EMAIL, new String[] {MailTo.parse(url).getTo()});
                    startActivity(intent);
                    return true;
                }
                else if (url.contains(feedbackUrl))
                {
                    if (submitFeedback(url.substring(
                            url.indexOf(feedbackUrl) + feedbackUrl.length())))
                    {
                        Toast.makeText(activity,
                                getString(R.string.FeedbackActivity_Success),
                                Toast.LENGTH_SHORT).show();
                        activity.finish();
                    }
                    else
                    {
                        Toast.makeText(activity,
                                getString(R.string.FeedbackActivity_Failure),
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
                return false;
            }
            
            private boolean submitFeedback(String urlParameters)
            {
                final String formDataParameterName = "formdata=";
                if (!urlParameters.startsWith(formDataParameterName))
                {
                    MyLog.w(R.string.FeedbackActivity_InvalidURLParameters);
                    return false;
                }

                ServerInterface serverInterface = new ServerInterface(activity);
                serverInterface.start();

                String formData;
                try
                {
                    formData = URLDecoder.decode(urlParameters.substring(formDataParameterName.length()), "utf-8");
                }
                catch (UnsupportedEncodingException e)
                {
                    MyLog.w(R.string.FeedbackActivity_SubmitFeedbackFailed, e);
                    return false;
                }
                
                // Hack to get around network/UI restriction. Still blocks the UI
                // for the duration of the request until timeout.
                // TODO: Actually run in the background
                class FeedbackRequestThread extends Thread
                {
                    private ServerInterface m_serverInterface;
                    private String m_formData;
                    private boolean m_success = false;
                    
                    FeedbackRequestThread(ServerInterface serverInterface, String formData)
                    {
                        m_serverInterface = serverInterface;
                        m_formData = formData;
                    }

                    public void run()
                    {
                        try
                        {
                            m_serverInterface.doFeedbackRequest(m_formData);
                            m_success = true;
                        }
                        catch (PsiphonServerInterfaceException e)
                        {
                        }
                    }
                    
                    public boolean getSuccess()
                    {
                        return m_success;
                    }
                }
                    
                try
                {
                    FeedbackRequestThread thread = new FeedbackRequestThread(serverInterface, formData);
                    thread.start();
                    thread.join();                 
                    return thread.getSuccess();
                }
                catch (InterruptedException e)
                {
                    MyLog.w(R.string.FeedbackActivity_SubmitFeedbackFailed, e);
                    return false;
                }
            }
        });

        // Load the feedback page
        String html = getHTMLContent();
        // Get the current locale
        String language = Locale.getDefault().getLanguage();
        webView.loadDataWithBaseURL("file:///#" + language, html, "text/html", "utf-8", null);
    }

    private String getHTMLContent()
    {
        String html = "";
        
        try
        {
            InputStream stream = getAssets().open("feedback.html");
            html = streamToString(stream);
        }
        catch (IOException e)
        {
            MyLog.w(R.string.FeedbackActivity_GetHTMLContentFailed, e);
            
            // Render the default text
            html = "<body>" + getString(R.string.FeedbackActivity_DefaultText) + "</body>";
        }
        
        return html;
    }

    private String streamToString(InputStream stream) throws IOException
    {
        try
        {
            Reader reader = new BufferedReader(new InputStreamReader(stream));
            Writer writer = new StringWriter();
        
            int n;
            char[] buffer = new char[2048];
            while ((n = reader.read(buffer)) != -1)
            {
                writer.write(buffer, 0, n);
            }
            return writer.toString();
        }
        finally
        {
            stream.close();
        }
    }
}
