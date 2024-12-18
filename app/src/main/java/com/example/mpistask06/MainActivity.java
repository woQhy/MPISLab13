package com.example.mpistask06;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private Button buttonAuthor, buttonDownload, buttonView, buttonDelete;
    private EditText inputJournalId;
    private File fileJournal;
    private TextView textJournalEmpty;
    private LinearLayout layoutJournalReady;
    private ProgressBar progressDownload;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textJournalEmpty = findViewById(R.id.textJournalEmpty);
        layoutJournalReady = findViewById(R.id.layoutJournalReady);
        progressDownload = findViewById(R.id.progressDownload);

        textJournalEmpty.setVisibility(View.VISIBLE);
        layoutJournalReady.setVisibility(View.GONE);
        progressDownload.setVisibility(View.GONE);

        inputJournalId = findViewById(R.id.inputJournalId);
        buttonDownload = findViewById(R.id.buttonDownload);
        buttonView = findViewById(R.id.buttonView);
        buttonDelete = findViewById(R.id.buttonDelete);

        showPopupWindow();

        buttonDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String journalId = inputJournalId.getText().toString().trim();
                if (journalId.isEmpty()) {
                    showAlertDialog(getString(R.string.alert_dialog_error), getString(R.string.alert_dialog_fillallfields));
                    return;
                }
                String fileUrl = "https://ntv.ifmo.ru/file/journal/" + journalId + ".pdf";
                String fileName = "journal_" + journalId + ".pdf";
                progressDownload.setVisibility(View.VISIBLE);
                new DownloadFileTask(MainActivity.this).execute(fileUrl, fileName);
            }
        });

        buttonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (fileJournal != null && fileJournal.exists()) {
                    Uri pdfUri = FileProvider.getUriForFile(MainActivity.this, MainActivity.this.getPackageName() + ".fileprovider", fileJournal);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(pdfUri, "application/pdf");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intent);
                } else {
                    showAlertDialog(getString(R.string.alert_dialog_error), getString(R.string.alert_dialog_nofile));
                }
            }
        });

        buttonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (fileJournal.exists()) {
                    fileJournal.delete();
                    buttonView.setEnabled(false);
                    buttonDelete.setEnabled(false);
                    showAlertDialog(getString(R.string.alert_dialog_success), getString(R.string.alert_dialog_delete));
                    textJournalEmpty.setVisibility(View.VISIBLE);
                    layoutJournalReady.setVisibility(View.GONE);
                }
            }
        });

        buttonAuthor = findViewById(R.id.authorBtn);

        buttonAuthor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showAlertDialog("Разработал", getString(R.string.author));
            }
        });
    }

    private void showPopupWindow() {
        SharedPreferences prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        boolean dontShowAgain = prefs.getBoolean("dontShowAgain", false);

        if (!dontShowAgain) {
            inputJournalId.post(() -> {
                View popupView = LayoutInflater.from(this).inflate(R.layout.popup, null);
                final PopupWindow popupWindow = new PopupWindow(popupView,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        true
                );

                CheckBox checkboxDontShow = popupView.findViewById(R.id.checkbox);
                Button buttonOk = popupView.findViewById(R.id.ok_button);

                buttonOk.setOnClickListener(v -> {
                    if (checkboxDontShow.isChecked()) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("dontShowAgain", true);
                        editor.apply();
                    }
                    popupWindow.dismiss();
                });

                popupWindow.showAtLocation(getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            });
        }
    }

    private class DownloadFileTask extends AsyncTask<String, Void, Boolean> {

        private Context context;
        private File folderJournal;

        public DownloadFileTask(Context context) {
            this.context = context;
            folderJournal = new File(context.getExternalFilesDir(null), "Journals");
            if (!folderJournal.exists()) {
                folderJournal.mkdirs();
            }
        }

        @Override
        protected Boolean doInBackground(String... urls) {
            runOnUiThread(() -> {
                progressDownload.setVisibility(View.VISIBLE);
                textJournalEmpty.setVisibility(View.GONE);
            });

            String fileUrl = urls[0];
            String fileName = urls[1];
            fileJournal = new File(folderJournal, fileName);

            try {

                progressDownload.setVisibility(View.VISIBLE);
                textJournalEmpty.setVisibility(View.GONE);

                URL url = new URL(fileUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    FileOutputStream outputStream = new FileOutputStream(fileJournal);

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    outputStream.close();
                    inputStream.close();
                    return true;
                } else {
                    InputStream errorStream = connection.getErrorStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                    String firstLine = reader.readLine();
                    if (firstLine.contains("<!DOCTYPE html>")) {
                        return false;
                    }
                    return false;
                }
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            } finally {
                runOnUiThread(() -> {
                    progressDownload.setVisibility(View.GONE);
                    if (fileJournal.exists()) {
                        layoutJournalReady.setVisibility(View.VISIBLE);
                    } else {
                        textJournalEmpty.setVisibility(View.VISIBLE);
                    }
                });
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                Toast.makeText(context, getString(R.string.alert_dialog_fileload), Toast.LENGTH_SHORT).show();
                buttonView.setEnabled(true);
                buttonDelete.setEnabled(true);
            } else {
                showAlertDialog(getString(R.string.alert_dialog_error), getString(R.string.alert_dialog_nofile));
                runOnUiThread(() -> {
                    layoutJournalReady.setVisibility(View.GONE);
                    textJournalEmpty.setVisibility(View.VISIBLE);
                });
            }
        }
    }

    private void showAlertDialog(String title, String message) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .create()
                .show();
    }
}