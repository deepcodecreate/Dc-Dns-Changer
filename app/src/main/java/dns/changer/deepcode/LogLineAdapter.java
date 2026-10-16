package dns.changer.deepcode;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogLineAdapter extends RecyclerView.Adapter<LogLineAdapter.LineHolder> {

    private final ArrayList<String> lines = new ArrayList<>();
    private int cap = 1000;

    public void setCap(int cap) {
        this.cap = Math.max(1, cap);
        boolean trimmed = false;
        while (lines.size() > this.cap) {
            lines.remove(0);
            trimmed = true;
        }
        if (trimmed) notifyDataSetChanged();
    }

    public void setInitialLines(List<String> initial) {
        lines.clear();
        lines.addAll(initial);
        while (lines.size() > cap) {
            lines.remove(0);
        }
        notifyDataSetChanged();
    }

    
    public void appendLine(String line) {
        if (lines.size() >= cap && !lines.isEmpty()) {
            lines.remove(0);
            notifyItemRemoved(0);
        }
        lines.add(line);
        notifyItemInserted(lines.size() - 1);
    }

    public void clearAll() {
        int size = lines.size();
        lines.clear();
        if (size > 0) notifyItemRangeRemoved(0, size);
    }

    public String getAllLogsText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    public int getLineCount() {
        return lines.size();
    }

    @NonNull
    @Override
    public LineHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log_line, parent, false);
        return new LineHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LineHolder holder, int position) {
        final String line = position >= 0 && position < lines.size() ? lines.get(position) : "";
        holder.bind(line);
        holder.itemView.setOnClickListener(v -> {
            if (line.isEmpty()) return;
            Context ctx = v.getContext();
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Log Line", line));
                boolean isEn = ctx.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE).getBoolean("english_language", false);
                Toast.makeText(ctx, isEn ? "Log line copied" : "خط لاگ کپی شد", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return lines.size();
    }

    static class LineHolder extends RecyclerView.ViewHolder {
        final TextView textView;

        LineHolder(@NonNull View itemView) {
            super(itemView);
            textView = (TextView) itemView;
        }

        void bind(String line) {
            textView.setText(Utils.colorizeWords(line));
        }
    }
}
