// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.self.imagecache.ImageLoader;

public class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.ViewHolder> {
    private final List<ApplicationItem> mItems = new ArrayList<>();
    private final OnArchiveClickListener mListener;

    public interface OnArchiveClickListener {
        void onArchiveClick(ApplicationItem item);
    }

    public SuggestionsAdapter(OnArchiveClickListener listener) {
        mListener = listener;
    }

    public void setItems(List<ApplicationItem> items) {
        mItems.clear();
        if (items != null) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_suggestion, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ApplicationItem item = mItems.get(position);
        holder.label.setText(item.label);
        ImageLoader.getInstance().displayImage(item.packageName, item, holder.icon);
        holder.btnArchive.setOnClickListener(v -> mListener.onArchiveClick(item));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        AppCompatImageView icon;
        TextView label;
        MaterialButton btnArchive;

        ViewHolder(View view) {
            super(view);
            icon = view.findViewById(R.id.icon);
            label = view.findViewById(R.id.label);
            btnArchive = view.findViewById(R.id.btn_archive);
        }
    }
}
