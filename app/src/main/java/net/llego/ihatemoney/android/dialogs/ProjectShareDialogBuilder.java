package net.llego.ihatemoney.android.dialogs;

import static androidx.core.content.ContextCompat.startActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.zxing.WriterException;

import net.llego.ihatemoney.R;
import net.llego.ihatemoney.model.DBProject;
import net.llego.ihatemoney.theme.ThemeUtils;
import net.llego.ihatemoney.theme.ThemedMaterialAlertDialogBuilder;
import net.llego.ihatemoney.util.ColorUtils;


public class ProjectShareDialogBuilder {

    private static final String TAG = ProjectShareDialogBuilder.class.getSimpleName();

    private final Context context;
    private final DBProject proj;

    public ProjectShareDialogBuilder(
            @NonNull Context context,
            @NonNull DBProject proj
    ) {
        this.context = context;
        this.proj = proj;
    }

    public AlertDialog show() {
        AlertDialog.Builder builder = new ThemedMaterialAlertDialogBuilder(context);

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_share_project, null);

        String shareUrl = proj.getShareUrl();
        String shareLink = "<a href=\"" + shareUrl + "\">" + shareUrl + "</a>";
        String publicWebUrl = proj.getPublicWebUrl();
        String publicWebLink = "<a href=\"" + publicWebUrl + "\">" + publicWebUrl + "</a>";

        TextView publicUrlTitle = view.findViewById(R.id.textViewShareProjectPublicUrlTitle);
        publicUrlTitle.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

        TextView qrCodeTitle = view.findViewById(R.id.textViewShareProjectQRCodeTitle);
        qrCodeTitle.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

        TextView publicUrlHint = view.findViewById(R.id.textViewShareProjectPublicUrlHint);
        publicUrlHint.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));

        TextView publicUrl = view.findViewById(R.id.textViewShareProjectPublicUrl);
        publicUrl.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        publicUrl.setText(Html.fromHtml(publicWebLink));
        publicUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(publicWebUrl));
                startActivity(context, i, null);
            }
        });

        TextView link = view.findViewById(R.id.textViewShareProject);
        link.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        link.setText(Html.fromHtml(shareLink));
        link.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                Toast.makeText(context, context.getString(R.string.qrcode_link_open_attempt_warning), Toast.LENGTH_SHORT).show();
            }
        });

        final var themeUtils = ThemeUtils.of(ColorUtils.primaryColor(view.getContext()), view.getContext());
        themeUtils.platform.colorTextViewLinks(publicUrl);
        themeUtils.platform.colorTextViewLinks(link);

        TextView hint = view.findViewById(R.id.textViewShareProjectHint);
        hint.setTextColor(ContextCompat.getColor(view.getContext(), R.color.fg_default_low));
        ImageView img = view.findViewById(R.id.imageViewShareProject);
        try {
            Bitmap bitmap = ColorUtils.encodeAsBitmap(shareUrl);
            img.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }

        builder.setView(view);
        builder.setTitle(context.getString(R.string.share_dialog_title));
        builder.setIcon(R.drawable.ic_share_grey_24dp);
        builder.setPositiveButton(context.getString(R.string.simple_ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setNeutralButton(context.getString(R.string.simple_share_share), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // share it
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, context.getString(R.string.share_share_intent_title, proj.getName()));
                shareIntent.putExtra(android.content.Intent.EXTRA_TEXT, shareUrl);
                Intent chooserIntent = Intent.createChooser(shareIntent, context.getString(R.string.share_share_chooser_title, proj.getName()));
                startActivity(context, chooserIntent, null);
            }
        });

        return builder.show();
    }

}
