package io.mrarm.irc.setup;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.exception.ZipExceptionConstants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.mrarm.irc.config.BackupManager;
import io.mrarm.irc.R;
import io.mrarm.irc.util.AppExecutors;
import io.mrarm.irc.util.DiagnosticLog;

public class BackupProgressActivity extends SetupProgressActivity {

    public static final String ARG_USER_PASSWORD = "password";
    public static final String ARG_RESTORE_MODE = "restore_mode";

    private boolean mRestoreMode = false;
    private File mBackupFile;
    private ActivityResultLauncher<Intent> mBackupFileLauncher;
    private ActivityResultLauncher<Intent> mBackupPasswordLauncher;
    private boolean mCompletionScheduled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerActivityResultLaunchers();

        if (getIntent().getBooleanExtra(ARG_RESTORE_MODE, false)) {
            mRestoreMode = true;
            DiagnosticLog.i(this, "BACKUP", () -> "Restore flow started");
            setTitle(R.string.title_activity_backup_progress_restore);
            askOpenBackup();
        } else {
            BackupRequest request = new BackupRequest();
            request.password = getIntent().getStringExtra(ARG_USER_PASSWORD);
            final boolean passwordProtected = request.password != null && !request.password.isEmpty();
            DiagnosticLog.i(this, "BACKUP", () ->
                    "Backup flow started passwordProtected=" + passwordProtected);
            acquireExitLock();
            new BackupTask(this).execute(request);
        }
    }

    private void registerActivityResultLaunchers() {
        mBackupFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::handleBackupFileResult);
        mBackupPasswordLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::handleBackupPasswordResult);
    }

    public void setDone(int resId) {
        if (mCompletionScheduled)
            return;
        mCompletionScheduled = true;
        DiagnosticLog.i(this, "BACKUP", () ->
                "Backup flow completion scheduled restoreMode=" + mRestoreMode +
                        ", resultResId=" + resId);
        releaseExitLock();
        // Activity results can be delivered while DocumentsUI is still completing its close
        // transition. Starting the completion activity immediately leaves the old progress
        // surface visible on some MIUI versions even though the restore has already finished.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed())
                return;
            Intent intent = new Intent(BackupProgressActivity.this,
                    BackupCompleteActivity.class);
            intent.putExtra(BackupCompleteActivity.ARG_DESC_TEXT, resId);
            if (mRestoreMode)
                intent.putExtra(BackupCompleteActivity.ARG_RESTORE_MODE, true);
            startNextActivity(intent);
        }, 350);
    }

    public void askOpenBackup() {
        DiagnosticLog.d(this, "BACKUP", () -> "Opening document picker for restore source");
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension("zip"));
        mBackupFileLauncher.launch(intent);
        setSlideAnimation(false);
    }

    public void onBackupDone(File file) {
        if (file == null) {
            DiagnosticLog.w(this, "BACKUP", () -> "Backup creation returned no file", null);
            setDone(R.string.error_generic);
            return;
        }
        DiagnosticLog.i(this, "BACKUP", () ->
                "Backup archive created bytes=" + file.length() + "; requesting destination");
        mBackupFile = file;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(MimeTypeMap.getSingleton().getMimeTypeFromExtension("zip"));
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "tiarca-backup-" + date + ".zip");
        mBackupFileLauncher.launch(intent);
        setSlideAnimation(false);
    }

    public void onRestoreDone(Integer result) {
        final int restoreResult = result == null ? -1 : result;
        DiagnosticLog.i(this, "BACKUP", () -> "Restore task completed result=" + restoreResult);
        if (result == RestoreTask.RESULT_OK) {
            setDone(R.string.backup_restored);
        } else if (result == RestoreTask.RESULT_ERROR) {
            setDone(R.string.error_generic);
        } else if (result == RestoreTask.RESULT_INVALID_PASSWORD) {
            Intent intent = new Intent(BackupProgressActivity.this, BackupPasswordActivity.class);
            intent.putExtra(BackupPasswordActivity.ARG_RESTORE_MODE, true);
            intent.putExtra(BackupPasswordActivity.ARG_WAS_INVALID, true);
            mBackupPasswordLauncher.launch(intent);
        }
    }

    public void onBackupCopyDone(boolean success) {
        DiagnosticLog.i(this, "BACKUP", () ->
                "Backup document copy completed success=" + success +
                        ", restoreMode=" + mRestoreMode);
        if (success) {
            if (mRestoreMode) {
                boolean valid = BackupManager.verifyBackupFile(mBackupFile);
                DiagnosticLog.d(this, "BACKUP", () -> "Restore archive validation valid=" + valid);
                if (!valid) {
                    mBackupFile.delete();
                    setDone(R.string.backup_restore_invalid);
                } else if (BackupManager.isBackupPasswordProtected(mBackupFile)) {
                    DiagnosticLog.i(this, "BACKUP", () ->
                            "Restore archive is password protected; requesting password");
                    Intent intent = new Intent(BackupProgressActivity.this,
                            BackupPasswordActivity.class);
                    intent.putExtra(BackupPasswordActivity.ARG_RESTORE_MODE, true);
                    mBackupPasswordLauncher.launch(intent);
                } else {
                    startRestoreTask(null);
                }
            } else {
                setDone(R.string.backup_created);
            }
        } else {
            setDone(R.string.error_generic);
        }
    }

    public void startRestoreTask(String password) {
        final boolean passwordProvided = password != null && !password.isEmpty();
        DiagnosticLog.i(this, "BACKUP", () ->
                "Starting restore task passwordProvided=" + passwordProvided);
        acquireExitLock();
        RestoreRequest request = new RestoreRequest();
        request.file = mBackupFile;
        request.password = password;
        new RestoreTask(this).execute(request);
    }

    public void cancel() {
        DiagnosticLog.i(this, "BACKUP", () ->
                "Backup flow cancelled restoreMode=" + mRestoreMode);
        if (mBackupFile != null)
            mBackupFile.delete();
        setDone(mRestoreMode ? R.string.backup_restore_cancelled : R.string.backup_cancelled);
        setSlideAnimation(true);
    }

    private void handleBackupFileResult(ActivityResult result) {
        Intent data = result.getData();
        if (data == null || data.getData() == null) {
            DiagnosticLog.d(this, "BACKUP", () -> "Document picker returned no URI");
            cancel();
            return;
        }
        try {
            Uri uri = data.getData();
            if (mRestoreMode) {
                DiagnosticLog.d(this, "BACKUP", () -> "Opening selected restore source");
                ParcelFileDescriptor desc = getContentResolver().openFileDescriptor(uri, "r");
                if (desc == null)
                    throw new IOException("Unable to open backup for reading");
                mBackupFile = new File(getCacheDir(), "temp-backup.zip");
                mBackupFile.deleteOnExit();
                FileInputStream fis = new FileInputStream(desc.getFileDescriptor());
                FileOutputStream fos = new FileOutputStream(mBackupFile);
                new CopyFileTask(this).execute(new CopyRequest(fis, fos, desc));
            } else {
                DiagnosticLog.d(this, "BACKUP", () -> "Opening selected backup destination");
                ParcelFileDescriptor desc = getContentResolver().openFileDescriptor(uri, "w");
                if (desc == null)
                    throw new IOException("Unable to open backup destination");
                FileOutputStream fos = new FileOutputStream(desc.getFileDescriptor());
                FileInputStream fis = new FileInputStream(mBackupFile);
                new CopyFileTask(this).execute(new CopyRequest(fis, fos, desc));
            }
        } catch (IOException e) {
            e.printStackTrace();
            DiagnosticLog.e(this, "BACKUP", () ->
                    "Unable to open backup source/destination restoreMode=" + mRestoreMode, e);
            setDone(R.string.error_generic);
        }
    }

    private void handleBackupPasswordResult(ActivityResult result) {
        Intent data = result.getData();
        if (result.getResultCode() == BackupPasswordActivity.RESULT_CODE_PASSWORD && data != null) {
            DiagnosticLog.d(this, "BACKUP", () -> "Restore password supplied by user");
            startRestoreTask(data.getStringExtra(BackupPasswordActivity.RET_PASSWORD));
        } else {
            DiagnosticLog.d(this, "BACKUP", () -> "Restore password prompt cancelled");
            cancel();
        }
    }

    private static class BackupRequest {
        public String password;
    }

    private static class BackupTask {
        private WeakReference<BackupProgressActivity> mActivity;
        private Context mContext;

        public BackupTask(BackupProgressActivity activity) {
            mActivity = new WeakReference<>(activity);
            mContext = activity.getApplicationContext();
        }

        public void execute(BackupRequest request) {
            DiagnosticLog.d(mContext, "BACKUP", () -> "Backup worker scheduled");
            AppExecutors.IO.execute(() -> {
                File file = createBackup(request);
                new Handler(Looper.getMainLooper()).post(() -> {
                    BackupProgressActivity activity = mActivity.get();
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                        activity.onBackupDone(file);
                });
            });
        }

        private File createBackup(BackupRequest request) {
            File backupFile = new File(mContext.getCacheDir(), "temp-backup.zip");
            if (backupFile.exists())
                backupFile.delete();
            backupFile.deleteOnExit(); // in case something fails
            try {
                DiagnosticLog.d(mContext, "BACKUP", () -> "Creating backup archive");
                BackupManager.createBackup(mContext, backupFile, request.password);
                DiagnosticLog.i(mContext, "BACKUP", () ->
                        "Backup archive generation succeeded bytes=" + backupFile.length());
            } catch (IOException e) {
                e.printStackTrace();
                DiagnosticLog.e(mContext, "BACKUP", () -> "Backup archive generation failed", e);
                backupFile.delete();
                return null;
            }
            return backupFile;
        }
    }


    private static class RestoreRequest {
        public File file;
        public boolean deleteFile = true;
        public String password;
    }

    private static class RestoreTask {
        public static final int RESULT_OK = 0;
        public static final int RESULT_ERROR = 1;
        public static final int RESULT_INVALID_PASSWORD = 2;

        private WeakReference<BackupProgressActivity> mActivity;
        private Context mContext;

        public RestoreTask(BackupProgressActivity activity) {
            mActivity = new WeakReference<>(activity);
            mContext = mActivity.get().getApplicationContext();
        }

        public void execute(RestoreRequest request) {
            DiagnosticLog.d(mContext, "BACKUP", () -> "Restore worker scheduled");
            AppExecutors.IO.execute(() -> {
                int result = restoreBackup(request);
                new Handler(Looper.getMainLooper()).post(() -> {
                    BackupProgressActivity activity = mActivity.get();
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                        activity.onRestoreDone(result);
                });
            });
        }

        private int restoreBackup(RestoreRequest request) {
            try {
                DiagnosticLog.d(mContext, "BACKUP", () -> "Restoring backup archive");
                BackupManager.restoreBackup(mContext, request.file, request.password);
                if (request.deleteFile)
                    request.file.delete();
                DiagnosticLog.i(mContext, "BACKUP", () -> "Restore archive applied successfully");
                return RESULT_OK;
            } catch (IOException e) {
                e.printStackTrace();
                if (e.getCause() instanceof ZipException &&
                        ((ZipException) e.getCause()).getCode() ==
                                ZipExceptionConstants.WRONG_PASSWORD) {
                    DiagnosticLog.w(mContext, "BACKUP", () ->
                            "Restore failed because archive password was rejected", e);
                    return RESULT_INVALID_PASSWORD;
                }
                DiagnosticLog.e(mContext, "BACKUP", () -> "Restore archive failed", e);
                if (request.deleteFile)
                    request.file.delete();
                return RESULT_ERROR;
            }
        }

    }


    private static class CopyRequest {
        public FileInputStream fis;
        public FileOutputStream fos;
        public ParcelFileDescriptor fd;
        public CopyRequest(FileInputStream fis, FileOutputStream fos, ParcelFileDescriptor fd) {
            this.fis = fis;
            this.fos = fos;
            this.fd = fd;
        }
    }

    private static class CopyFileTask {
        private WeakReference<BackupProgressActivity> mActivity;
        private Context mContext;

        public CopyFileTask(BackupProgressActivity activity) {
            mActivity = new WeakReference<>(activity);
            mContext = activity.getApplicationContext();
        }

        public void execute(CopyRequest request) {
            DiagnosticLog.d(mContext, "BACKUP", () -> "Document copy worker scheduled");
            AppExecutors.IO.execute(() -> {
                boolean success = copyFile(request);
                new Handler(Looper.getMainLooper()).post(() -> {
                    BackupProgressActivity activity = mActivity.get();
                    if (activity != null && !activity.isFinishing() && !activity.isDestroyed())
                        activity.onBackupCopyDone(success);
                });
            });
        }

        private boolean copyFile(CopyRequest request) {
            long total = 0;
            try {
                FileInputStream fis = request.fis;
                FileOutputStream fos = request.fos;
                byte[] buf = new byte[1024 * 16];
                int c;
                while ((c = fis.read(buf, 0, buf.length)) > 0) {
                    fos.write(buf, 0, c);
                    total += c;
                }
                fis.close();
                fos.close();
                final long copiedBytes = total;
                DiagnosticLog.i(mContext, "BACKUP", () ->
                        "Document copy succeeded bytes=" + copiedBytes);
            } catch (Exception e) {
                e.printStackTrace();
                final long copiedBytes = total;
                DiagnosticLog.e(mContext, "BACKUP", () ->
                        "Document copy failed afterBytes=" + copiedBytes, e);
                return false;
            } finally {
                try {
                    if (request.fd != null)
                        request.fd.close();
                } catch (Exception e) {
                    DiagnosticLog.w(mContext, "BACKUP", () ->
                            "Closing document descriptor failed", e);
                }
            }
            return true;
        }
    }

}
