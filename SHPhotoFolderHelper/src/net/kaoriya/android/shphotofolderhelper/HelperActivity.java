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
        setContentView(R.layout.helper);

        findViewById(R.id.MoveToCamera).setOnClickListener(this);
        findViewById(R.id.InstallHooks).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        try {
            switch (v.getId()) {

            case R.id.MoveToCamera:
                MoveToCameraTask task = new MoveToCameraTask(this);
                task.execute();
                break;

            case R.id.InstallHooks:
                // TODO: install hooks.
                break;

            }
        } catch (Exception e) {
            Log.e(TAG, "#onClick failed", e);
        }
    }

}
