package com.matburt.mobileorg.Gui.Help;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.matburt.mobileorg.R;
import com.matburt.mobileorg.util.OrgUtils;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        OrgUtils.setTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        RecyclerView recycler = findViewById(R.id.help_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new HelpAdapter());

        TextView version = findViewById(R.id.help_about_version);
        TextView repo = findViewById(R.id.help_about_repo);
        TextView license = findViewById(R.id.help_about_license);
        repo.setText(R.string.help_about_repo);
        license.setText(R.string.help_about_license);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText(getString(R.string.help_about_version, info.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            version.setText("");
        }
    }

    private class HelpAdapter extends RecyclerView.Adapter<HelpAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            ViewHolder(TextView itemView) {
                super(itemView);
                title = itemView;
            }
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextView view = (TextView) LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_help_topic, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final HelpTopic topic = HelpTopic.TOPICS[position];
            holder.title.setText(topic.titleRes);
            holder.title.setOnClickListener(v ->
                    startActivity(new Intent(HelpActivity.this,
                            HelpDetailActivity.class)
                            .putExtra(HelpDetailActivity.EXTRA_ASSET_PATH,
                                    HelpTopic.getAssetPath(HelpActivity.this, topic))));
        }

        @Override
        public int getItemCount() {
            return HelpTopic.TOPICS.length;
        }
    }
}
