package com.vby.twopass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.Metadata;
import com.google.android.gms.drive.MetadataBuffer;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.DriveApi.ContentsResult;
import com.google.android.gms.drive.DriveApi.DriveIdResult;
import com.google.android.gms.drive.DriveApi.MetadataBufferResult;
import com.google.android.gms.drive.DriveFolder.DriveFileResult;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;

import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class TwoPassMainActivity extends ActionBarActivity
     implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleApiClient mGoogleApiClient;

    private static final String TAG = "TwoPassMainActivity";
    // Request code for auto Google Play Services error resolution.
    private static final int REQUEST_CODE_RESOLUTION = 1;
    private static final String PW_FILE = "pw.txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_two_pass_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }

        mGoogleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Drive.API)
            .addScope(Drive.SCOPE_FILE)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.two_pass_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_RESOLUTION:
                if (resultCode == RESULT_OK) {
                    mGoogleApiClient.connect();
                }
                break;
        }
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_two_pass_main, container, false);
            return rootView;
        }
    }

    // ==========================
    // OnConnectionFailedListener
    // ==========================

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.i(TAG, "GoogleApiClient connection failed: " + result.toString());
        if (!result.hasResolution()) {
            // show the localized error dialog.
            GooglePlayServicesUtil.getErrorDialog(result.getErrorCode(), this, 0).show();
            return;
        }
        try {
            result.startResolutionForResult(this, REQUEST_CODE_RESOLUTION);
        } catch (SendIntentException e) {
            Log.e(TAG, "Exception while starting resolution activity", e);
        }
    }

    // ==========================
    // ConnectionCallbacks method
    // ==========================

    @Override
    public void onConnected(Bundle connectionHint) {
        Log.i(TAG, "GoogleApiClient connected");
        Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, PW_FILE)).build();
        Drive.DriveApi.query(mGoogleApiClient, query).setResultCallback(metadataCallback);
    }

    final private ResultCallback<MetadataBufferResult> metadataCallback = new
            ResultCallback<MetadataBufferResult>() {
        @Override
        public void onResult(MetadataBufferResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.e(TAG, "Problem while retrieving query results");
                return;
            }
            MetadataBuffer metabuffer = result.getMetadataBuffer();
            if (metabuffer.getCount() == 1) {
                Metadata meta = metabuffer.get(0);
                DriveId id = meta.getDriveId();
                new RetrieveDriveFileContentsAsyncTask(TwoPassMainActivity.this).execute(id);
            } else {
                Log.d(TAG, "file: " + PW_FILE + " not existed");
                Drive.DriveApi.newContents(mGoogleApiClient).setResultCallback(contentsCallback);
            }
        }
    };

    final private ResultCallback<ContentsResult> contentsCallback = new
            ResultCallback<ContentsResult>() {
        @Override
        public void onResult(ContentsResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.e(TAG, "Error while trying to create new file contents");
                return;
            }

            MetadataChangeSet changeSet = new MetadataChangeSet.Builder()
                    .setTitle(PW_FILE)
                    .setMimeType("text/plain")
                    .setStarred(true).build();
            // create a file on root folder
            Drive.DriveApi.getRootFolder(mGoogleApiClient)
                    .createFile(mGoogleApiClient, changeSet, result.getContents())
                    .setResultCallback(fileCallback);
        }
    };

    final private ResultCallback<DriveFileResult> fileCallback = new
            ResultCallback<DriveFileResult>() {
        @Override
        public void onResult(DriveFileResult result) {
            if (!result.getStatus().isSuccess()) {
                Log.e(TAG, "Error while trying to create the file");
                return;
            }
            Log.e(TAG, "Created a file: " + result.getDriveFile().getDriveId());
            DriveFile file = result.getDriveFile();
            new EditContentsAsyncTask(TwoPassMainActivity.this).execute(file);
        }
    };

    final private class RetrieveDriveFileContentsAsyncTask extends
            ApiClientAsyncTask<DriveId, Boolean, String> {

        public RetrieveDriveFileContentsAsyncTask(Context context) {
            super(context);
        }

        @Override
        protected String doInBackgroundConnected(DriveId... params) {
            String contents = null;
            DriveFile file = Drive.DriveApi.getFile(getGoogleApiClient(),
                    params[0]);
            ContentsResult contentsResult = file.openContents(
                    getGoogleApiClient(), DriveFile.MODE_READ_ONLY, null)
                    .await();
            if (!contentsResult.getStatus().isSuccess()) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    contentsResult.getContents().getInputStream()));
            StringBuilder builder = new StringBuilder();
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
                contents = builder.toString();
            } catch (IOException e) {
                Log.e(TAG, "IOException while reading from the stream", e);
            }

            file.discardContents(getGoogleApiClient(),
                    contentsResult.getContents()).await();
            return contents;
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            if (result == null) {
                Log.e(TAG, "Error while reading from the file");
                return;
            }
            Log.d(TAG, "File contents: " + result);
            TextView textview = (TextView)TwoPassMainActivity.this.findViewById(R.id.main_content);
            textview.setText(result);
        }
    }

    public class EditContentsAsyncTask extends ApiClientAsyncTask<DriveFile, Void, Boolean> {

        public EditContentsAsyncTask(Context context) {
            super(context);
        }

        @Override
        protected Boolean doInBackgroundConnected(DriveFile... args) {
            DriveFile file = args[0];
            try {
                ContentsResult contentsResult = file.openContents(
                        getGoogleApiClient(), DriveFile.MODE_WRITE_ONLY, null).await();
                if (!contentsResult.getStatus().isSuccess()) {
                    return false;
                }
                OutputStream outputStream = contentsResult.getContents().getOutputStream();
                outputStream.write("Hello world".getBytes());
                com.google.android.gms.common.api.Status status = file.commitAndCloseContents(
                        getGoogleApiClient(), contentsResult.getContents()).await();
                return status.getStatus().isSuccess();
            } catch (IOException e) {
                Log.e(TAG, "IOException while appending to the output stream", e);
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                Log.e(TAG, "Error while editing contents");
                return;
            }
            Log.e(TAG, "Successfully edited contents");
        }
    }

    @Override
    public void onConnectionSuspended(int arg0) {
        Log.i(TAG, "GoogleApiClient connection suspended");
    }

}
