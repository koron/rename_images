package net.kaoriya.android.shphotofolderhelper;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

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
        PhotoFolderHelper helper = new PhotoFolderHelper(this);
        helper.moveToCamera(true);
    }

}
