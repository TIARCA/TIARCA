package io.mrarm.irc.guide;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.mrarm.irc.R;

/** Searchable expandable topics, rendered using native Android views for accessibility/themes. */
public final class GuideAdapter extends RecyclerView.Adapter<GuideAdapter.Holder> {
    public interface CommandListener {
        void onCopy(String syntax);
        void onInsert(String syntax);
    }

    private final Context context;
    private final CommandListener listener;
    private final boolean allowInsert;
    private final Set<String> expanded = new HashSet<>();
    private final List<GuideCatalog.Entry> shown = new ArrayList<>();

    public GuideAdapter(Context context, CommandListener listener, boolean allowInsert) {
        this.context = context;
        this.listener = listener;
        this.allowInsert = allowInsert;
    }

    public void show(List<GuideCatalog.Entry> entries) {
        shown.clear();
        shown.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.guide_entry, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        GuideCatalog.Entry item = shown.get(position);
        String categoryLabel = "";
        for (int i = 1; i < GuideCatalog.CATEGORIES.length; i++) {
            if (GuideCatalog.CATEGORIES[i].equals(item.category)) {
                categoryLabel = context.getResources().getStringArray(R.array.guide_categories)[i];
                break;
            }
        }
        boolean newSection = position == 0 || !item.category.equals(shown.get(position - 1).category);
        holder.section.setVisibility(newSection ? View.VISIBLE : View.GONE);
        holder.section.setText(categoryLabel);
        holder.title.setText(item.title);
        holder.summary.setText(item.summary);
        boolean open = expanded.contains(item.id);
        holder.detail.setVisibility(open ? View.VISIBLE : View.GONE);
        holder.header.setContentDescription(item.title + ". " + item.summary);
        holder.header.setOnClickListener(view -> {
            if (!expanded.add(item.id))
                expanded.remove(item.id);
            int p = holder.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION)
                notifyItemChanged(p);
        });
        if (!open)
            return;
        holder.body.setText(item.body);
        setOptional(holder.syntax, item.syntax.isEmpty() ? "" :
                context.getString(R.string.guide_syntax_label, item.syntax));
        setOptional(holder.example, item.example.isEmpty() ? "" :
                context.getString(R.string.guide_example_label, item.example));
        setOptional(holder.note, item.note);
        setOptional(holder.source, item.source.isEmpty() ? "" :
                context.getString(R.string.guide_source_label, item.source));
        boolean command = item.syntax.startsWith("/") && !item.syntax.contains("\n")
                && !item.syntax.contains("\r");
        holder.copy.setVisibility(command ? View.VISIBLE : View.GONE);
        holder.insert.setVisibility(command && allowInsert ? View.VISIBLE : View.GONE);
        holder.copy.setOnClickListener(view -> listener.onCopy(item.syntax));
        holder.insert.setOnClickListener(view -> listener.onInsert(item.syntax));
    }

    private static void setOptional(TextView view, String value) {
        view.setText(value);
        view.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override public int getItemCount() { return shown.size(); }

    public static final class Holder extends RecyclerView.ViewHolder {
        final TextView section, title, summary, body, syntax, example, note, source;
        final View header, detail;
        final Button copy, insert;

        Holder(View view) {
            super(view);
            section = view.findViewById(R.id.guide_section);
            title = view.findViewById(R.id.guide_item_title);
            summary = view.findViewById(R.id.guide_item_summary);
            header = view.findViewById(R.id.guide_header);
            detail = view.findViewById(R.id.guide_detail);
            body = view.findViewById(R.id.guide_body);
            syntax = view.findViewById(R.id.guide_syntax);
            example = view.findViewById(R.id.guide_example);
            note = view.findViewById(R.id.guide_note);
            source = view.findViewById(R.id.guide_source);
            copy = view.findViewById(R.id.guide_copy);
            insert = view.findViewById(R.id.guide_insert);
        }
    }
}
