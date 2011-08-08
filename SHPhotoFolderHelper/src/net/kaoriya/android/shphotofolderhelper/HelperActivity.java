package net.kaoriya.android.shphotofolderhelper;

import android.app.Activity;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

public class HelperActivity
    extends Activity
    implements View.OnClickListener, ISHPhotoFolderHelper
{

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        findViewById(R.id.MoveToCamera).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        try {
            switch (v.getId()) {
            case R.id.MoveToCamera:
                moveToCamera();
                break;
            }
        } catch (Exception e) {
            Log.e(TAG, "#onClick failed", e);
        }
    }

    private void moveToCamera() throws Exception {
        // FIXME: AsyncTask化する. でないとダイアログが表示されない.

        // プログレスダイアログを表示.
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Processing");
        progress.setIndeterminate(true);
        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progress.setCancelable(false);
        progress.show();

        // 処理実行
        int count = 0;
        try {
            PhotoFolderHelper helper = new PhotoFolderHelper(this);
            count = helper.moveToCamera(true);
        } catch (Exception e) {
            Log.e(TAG, "#moveToCamera failed", e);
            count = -1;
        }

        // プログレスダイアログを消し、Toastで結果報告.
        progress.dismiss();
        final Toast toast;
        if (count > 0) {
            toast = Toast.makeText(this, "Process " + count + "images",
                    Toast.LENGTH_SHORT);
        } else if (count == 0) {
            toast = Toast.makeText(this, "No images processed",
                    Toast.LENGTH_SHORT);
        } else {
            toast = Toast.makeText(this, "Got error", Toast.LENGTH_SHORT);
        }
        toast.show();
    }

}
