package net.kaoriya.android.shphotofolderhelper;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public class AssortPhotosActivity
    extends Activity
    implements ISHPhotoFolderHelper
{

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        MoveToCameraTask task = new MoveToCameraTask(this) {
            @Override
            protected void onPostExecute(Integer result) {
                super.onPostExecute(result);
                AssortPhotosActivity.this.finish();
            }
        };

        try {
            task.execute();
        } catch (Exception e) {
            Log.e(TAG, "task failed", e);
        }
    }

}
