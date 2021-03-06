package net.kaoriya.android.shphotofolderhelper;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.Context;
import android.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.os.Environment;
import android.provider.MediaStore.Images;
import android.util.Log;

public final class PhotoFolderHelper implements ISHPhotoFolderHelper
{

    private static final Pattern JPEG_PATTERN = Pattern.compile(
            "\\.(?:JPG|JPEG)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OUTPUT_PATTERN = Pattern.compile(
            "^IMG_\\d+_\\d+(_\\d+)?\\.(?:JPG|JPEG)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "^(\\d\\d\\d\\d):(\\d\\d):(\\d\\d) (\\d\\d):(\\d\\d):(\\d\\d)$");

    private final Context context;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat(
            "yyyyMMdd_HHmmss");

    // FIXME: このメモリ馬鹿食いの構造を改める.
    private static class MoveResults {
        final ArrayList<String> addedPath = new ArrayList<String>();
        final ArrayList<String> removedPath = new ArrayList<String>();
        final ArrayList<String> movedPath = new ArrayList<String>();
        void add(File src, File dst) {
            String srcPath = src.getAbsolutePath();
            String dstPath = dst.getAbsolutePath();
            this.addedPath.add(dstPath);
            this.movedPath.add(dstPath);
            this.removedPath.add(srcPath);
            this.movedPath.add(srcPath);
        }
    }

    public PhotoFolderHelper(Context context) {
        this.context = context;
    }

    /**
     * Move photo files to specified folder.
     *
     * SHARPのカメラアプリが出力した写真ファイルを、Androidの標準カメラの出力方
     * 式に合わせて、親フォルダと名前を変更する.
     *
     * 移動の手順:
     *   1. 入力フォルダと出力フォルダを決定
     *   2. (必要ならば)SDカードが未接続なら終了.
     *   3. 入力フォルダがなければ終了.
     *   4. 出力フォルダを作れなければ終了.
     *   5. 入力フォルダ内の各ファイルを処理
     *     1. ファイル名が規則に従っていれば次のファイルへ
     *     2. EXIFなどから撮影日時(変更後の名称コア文字列)を決定.
     *     3. 出力ファイル名を決定
     *     4. 実行フラグチェック
     *       1. ファイル移動
     *       2. mtime変更
     *   6. ギャラリーの一覧を更新する.
     */
    public int moveToCamera(boolean executionFlag)
        throws Exception
    {
        // 1. 入力フォルダと出力フォルダを決定
        File inDir = getInDir();
        File outDir = getOutDir();

        // 2. (必要ならば)SDカードが未接続なら終了.
        if ((isRequireSDCard(outDir) || isRequireSDCard(inDir)) &&
                !isSDCardReady())
        {
            Log.w(TAG, "SD card is not ready");
            return -1;
        }

        // 3. 入力フォルダがなければ終了.
        if (!inDir.exists() || !inDir.isDirectory() || !inDir.canRead()) {
            Log.w(TAG, "inDir isn't available: " + inDir.toString());
            return -1;
        }

        // 4. 出力フォルダを作れなければ終了.
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
        if (!outDir.isDirectory() || !outDir.canWrite()) {
            Log.w(TAG, "outDir isn't available: " + outDir.toString());
            return -1;
        }

        // 5. 入力フォルダ内の各ファイルを処理
        MoveResults results = new MoveResults();
        int count = moveToCamera(executionFlag, inDir, outDir, results);

        // 6. ギャラリーの一覧を更新する.
        if (results.movedPath.size() > 0) {
            // 移動先(追加分)を反映させる.
            String[] path = results.addedPath.toArray(new String[0]);
            MediaScannerConnection.scanFile(this.context, path, null, null);
            // 移動元(削除分)を反映させる.
            ContentResolver cr = this.context.getContentResolver();
            for (String deleted : results.removedPath) {
                cr.delete(Images.Media.EXTERNAL_CONTENT_URI,
                        Images.Media.DATA + "=?",
                        new String[] { deleted });
            }
        }

        return count;
    }

    private int moveToCamera(
            boolean executionFlag,
            File inDir,
            File outDir,
            MoveResults results)
        throws Exception
    {
        int count = 0;

        // 5. 入力フォルダ内の各ファイルを処理
        Log.i(TAG, "#moveToCamera inDir=" + inDir.toString());

        ArrayList<File> subdirs = new ArrayList<File>();
        for (File entry : inDir.listFiles()) {
            if (entry.isDirectory()) {
                // サブフォルダの再帰処理を予約.
                if (!entry.getName().startsWith(".") && !entry.equals(outDir))
                {
                    subdirs.add(entry);
                } else {
                    Log.i(TAG, "skip reserved subdir: " + entry.toString());
                }
            } else if (entry.isFile() && entry.canRead()) {
                // 5-1. ファイル名が規則に従っていれば次のファイルへ
                if (isMoveToTarget(entry)) {
                    try {
                        count += movePhotoTo(executionFlag, entry, outDir,
                                results);
                    } catch (Exception e) {
                        Log.w(TAG, "failed: " + entry.toString(), e);
                    }
                }
            }
        }

        // 子フォルダを再帰処理.
        for (File subdir : subdirs) {
            count += moveToCamera(executionFlag, subdir, outDir, results);
        }

        return count;
    }

    private int movePhotoTo(
            boolean executionFlag,
            File photoFile,
            File outDir,
            MoveResults results)
        throws Exception
    {
        // 5-2. EXIFなどから撮影日時(変更後の名称コア文字列)を決定.
        String datetimeString = getCreationDatetimeString(photoFile);
        if (datetimeString == null) {
            Log.w(TAG, "invalid creation time: " + photoFile.toString());
            return 0;
        }

        // 5-3. 出力ファイル名を決定
        File destFile = getOutputFile(outDir, datetimeString, photoFile);
        if (destFile == null) {
            Log.w(TAG, "invalid destination: core=" + datetimeString + " " +
                    photoFile.toString());
            return 0;
        }

        // 5-4-2. mtime変更
        long lastModified = photoFile.lastModified();
        long expected = parseDatetime(datetimeString);
        if (expected > 0 && Math.abs(expected - lastModified) > 30000) {
            Log.v(TAG, "fix mtime: " + photoFile.toString() + " (" +
                    lastModified + " -> " + expected);
            if (executionFlag) {
                photoFile.setLastModified(expected);
            }
        }

        // 5-4-1. ファイル移動
        Log.v(TAG, "rename " + photoFile.toString() + " to " +
                destFile.toString());
        if (executionFlag) {
            photoFile.renameTo(destFile);
            // 結果を記録しておく.
            results.add(photoFile, destFile);
        }

        return 1;
    }

    private File getExternalDir()
    {
        File exdir = Environment.getExternalStorageDirectory();

        File exdir2 = new File(exdir, "external_sd");
        if (exdir2.isDirectory() && exdir2.canWrite()) {
            return exdir2;
        }

        return exdir;
    }

    private File getInDir() {
        // FIXME: make value customizable.
        //String name = "DCIM/100SHARP";
        String name = "DCIM/100ANDRO";
        File dir = new File(getExternalDir(), name);
        return dir;
    }

    private File getOutDir() {
        // FIXME: make value customizable.
        String name = "DCIM/Camera";
        File dir = new File(getExternalDir(), name);
        return dir;
    }

    private boolean isRequireSDCard(File file) {
        File ext = getExternalDir();
        for (File parent = file.getParentFile(); parent != null;
                parent = parent.getParentFile())
        {
            if (parent.equals(ext)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSDCardReady() {
        return Environment.MEDIA_MOUNTED.equals(
                Environment.getExternalStorageState());
    }

    private boolean isMoveToTarget(File file) {
        boolean retval = false;
        String name = file.getName();
        if (JPEG_PATTERN.matcher(name).find()) {
            if (!OUTPUT_PATTERN.matcher(name).find()) {
                retval = true;
            }
        }
        return retval;
    }

    /**
     * Get photo creation datetime from file.
     *
     * 画像ファイルから撮影日時もしくは作成日時を取得し、フォーマットして返す.
     *
     * 書式: yyyyMMdd_HHmmss (this.dateFormat)
     */
    private String getCreationDatetimeString(File photoFile)
        throws IOException
    {
        ExifInterface exif = new ExifInterface(photoFile.getAbsolutePath());
        String datetime = exif.getAttribute(ExifInterface.TAG_DATETIME);
        if (datetime != null) {
            Matcher m = DATETIME_PATTERN.matcher(datetime);
            if (m.find()) {
                StringBuilder s = new StringBuilder();
                s.append(m.group(1));
                s.append(m.group(2));
                s.append(m.group(3));
                s.append('_');
                s.append(m.group(4));
                s.append(m.group(5));
                s.append(m.group(6));
                return s.toString();
            }
        }

        // ファイルの最終変更時刻を利用する.
        long time = photoFile.lastModified();
        if (time != 0) {
            return this.dateFormat.format(new Date(time));
        }

        // 撮影日時を決められない場合はnullを返す.
        return null;
    }

    private File getOutputFile(
            File outDir,
            String coreName,
            File inFile)
    {
        String baseName = "IMG_" + coreName;
        for (int i = 0; i < 1000; ++i) {
            // ファイル名を構成する.
            StringBuilder s = new StringBuilder(baseName);
            if (i != 0) {
                if (i < 10) {
                    s.append("00");
                } else if (i < 100) {
                    s.append("0");
                }
                s.append(i);
            }
            s.append(".JPG");

            File outFile = new File(outDir, s.toString());
            if (!outFile.exists()) {
                if (i != 0) {
                    Log.d(TAG, " append sub sequence: " + inFile.toString() +
                            " to " + outFile.toString());
                }
                return outFile;
            }
        }
        return null;
    }

    private long parseDatetime(String datetime) {
        try {
            return this.dateFormat.parse(datetime).getTime();
        } catch (ParseException e) {
            Log.w(TAG, "parseDatetime failed: " + datetime, e);
            return 0;
        }
    }
}
