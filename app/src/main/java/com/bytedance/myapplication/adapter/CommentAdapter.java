package com.bytedance.myapplication.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bytedance.myapplication.R;
import com.bytedance.myapplication.model.Comment;
import com.bytedance.myapplication.utils.ViewPoolManager;

import java.util.ArrayList;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {
    private List<Comment> comments;

    public CommentAdapter() {
        this.comments = new ArrayList<>();
    }

    public void addComment(Comment comment) {
        comments.add(comment);
        notifyItemInserted(comments.size() - 1);
    }

    public void setComments(List<Comment> newComments) {
        if (newComments == null) {
            newComments = new ArrayList<>();
        }
        
        // 优化：使用 DiffUtil 只更新变化的部分，避免全量重绘
        // 保持评论实时更新，但减少 UI 重绘开销
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
            new CommentDiffCallback(this.comments, newComments)
        );
        this.comments = newComments;
        diffResult.dispatchUpdatesTo(this);
    }
    
    /**
     * DiffUtil 回调，用于比较新旧评论列表
     */
    private static class CommentDiffCallback extends DiffUtil.Callback {
        private final List<Comment> oldList;
        private final List<Comment> newList;
        
        public CommentDiffCallback(List<Comment> oldList, List<Comment> newList) {
            this.oldList = oldList != null ? oldList : new ArrayList<>();
            this.newList = newList != null ? newList : new ArrayList<>();
        }
        
        @Override
        public int getOldListSize() {
            return oldList.size();
        }
        
        @Override
        public int getNewListSize() {
            return newList.size();
        }
        
        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            Comment oldComment = oldList.get(oldItemPosition);
            Comment newComment = newList.get(newItemPosition);
            // 使用评论 ID 作为唯一标识
            if (oldComment.getId() != null && newComment.getId() != null) {
                return oldComment.getId().equals(newComment.getId());
            }
            // 如果没有 ID，使用内容和名称组合作为标识
            return oldComment.getComment() != null && 
                   oldComment.getComment().equals(newComment.getComment()) &&
                   oldComment.getName() != null &&
                   oldComment.getName().equals(newComment.getName());
        }
        
        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Comment oldComment = oldList.get(oldItemPosition);
            Comment newComment = newList.get(newItemPosition);
            // 比较评论内容是否完全相同
            if (oldComment.getComment() == null && newComment.getComment() != null) {
                return false;
            }
            if (oldComment.getComment() != null && !oldComment.getComment().equals(newComment.getComment())) {
                return false;
            }
            if (oldComment.getName() == null && newComment.getName() != null) {
                return false;
            }
            if (oldComment.getName() != null && !oldComment.getName().equals(newComment.getName())) {
                return false;
            }
            return true;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 优化：优先从 View 池中获取 View，减少 inflate 开销
        ViewPoolManager viewPoolManager = ViewPoolManager.getInstance();
        View view = viewPoolManager.getViewFromPool(R.layout.item_comment);
        
        if (view == null) {
            // 如果池中没有，则创建新的 View
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_comment, parent, false);
        } else {
            // 从池中获取的 View，需要确保没有旧的父容器引用
            if (view.getParent() != null) {
                ((ViewGroup) view.getParent()).removeView(view);
            }
            // 重要：重置布局参数，确保宽度正确
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null || !(params instanceof androidx.recyclerview.widget.RecyclerView.LayoutParams)) {
                params = new androidx.recyclerview.widget.RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                );
                view.setLayoutParams(params);
            }
        }
        
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Comment comment = comments.get(position);
        holder.comment = comment;
        
        // 重置所有状态，确保从ViewPool获取的View不会保留之前的状态
        holder.detailLayout.setVisibility(View.GONE);
        holder.commentText.setMaxLines(Integer.MAX_VALUE);
        holder.commentText.setEllipsize(null);
        
        // 重要：重置TextView的宽度约束，确保占满整行
        // 从ViewPool获取的View可能保留了之前的测量结果
        ViewGroup.LayoutParams textParams = holder.commentText.getLayoutParams();
        if (textParams != null) {
            textParams.width = 0; // 0dp 表示使用 ConstraintLayout 的约束
            holder.commentText.setLayoutParams(textParams);
        }
        // 强制重新请求布局
        holder.commentText.requestLayout();
        
        String name = comment.getName() != null ? comment.getName() : "匿名用户";
        holder.nameText.setText(name);
        
        String originalCommentText = comment.getComment() != null ? comment.getComment() : "";
        String displayText;
        boolean isTruncated = false;
        int maxLength = 100;
        if (originalCommentText.length() > maxLength) {
            displayText = originalCommentText.substring(0, maxLength) + "...";
            isTruncated = true;
            holder.commentText.setMaxLines(2);
            holder.commentText.setEllipsize(android.text.TextUtils.TruncateAt.END);
        } else {
            displayText = originalCommentText;
        }
        holder.commentText.setText(displayText);
        holder.isTruncated = isTruncated;
        
        // 确保整个item重新布局
        holder.itemView.requestLayout();
        
        String avatarUrl = comment.getAvatar();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .fallback(R.mipmap.ic_launcher)
                    .into(holder.avatarImage);
        } else {
            holder.avatarImage.setImageResource(R.mipmap.ic_launcher);
        }
        
        String fullCommentText = originalCommentText.isEmpty() ? "（无内容）" : originalCommentText;
        holder.detailComment.setText(fullCommentText);
        holder.detailComment.setVisibility(View.VISIBLE);
        
        if (holder.detailScroll != null) {
            holder.detailScroll.setVisibility(View.VISIBLE);
        }
        
        holder.detailName.setText(name);
        holder.detailName.setVisibility(View.VISIBLE);
        
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.mipmap.ic_launcher)
                    .error(R.mipmap.ic_launcher)
                    .fallback(R.mipmap.ic_launcher)
                    .into(holder.detailAvatar);
        } else {
            holder.detailAvatar.setImageResource(R.mipmap.ic_launcher);
        }
        holder.detailAvatar.setVisibility(View.VISIBLE);
        
        holder.itemView.setOnClickListener(v -> toggleDetailView(holder));
        holder.detailClose.setOnClickListener(v -> toggleDetailView(holder));
        
        holder.itemView.setAlpha(isTruncated ? 0.9f : 1.0f);
    }
    
    private void toggleDetailView(ViewHolder holder) {
        if (holder.detailLayout.getVisibility() == View.VISIBLE) {
            holder.detailLayout.setVisibility(View.GONE);
        } else {
            holder.detailLayout.setVisibility(View.VISIBLE);
            
            if (holder.detailScroll != null) {
                holder.detailScroll.post(() -> {
                    holder.detailScroll.scrollTo(0, 0);
                    holder.detailScroll.setNestedScrollingEnabled(true);
                });
            }
            
            if (holder.itemView.getParent() instanceof RecyclerView) {
                RecyclerView recyclerView = (RecyclerView) holder.itemView.getParent();
                int position = holder.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    recyclerView.post(() -> recyclerView.smoothScrollToPosition(position));
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        TextView nameText;
        TextView commentText;
        Comment comment;  // 保存原始评论对象
        boolean isTruncated = false;  // 标记是否被截断
        
        // 详情视图组件
        ConstraintLayout detailLayout;
        ImageView detailAvatar;
        TextView detailName;
        TextView detailComment;
        TextView detailClose;
        android.widget.ScrollView detailScroll;

        ViewHolder(View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.comment_avatar);
            nameText = itemView.findViewById(R.id.comment_name);
            commentText = itemView.findViewById(R.id.comment_text);
            
            // 详情视图
            detailLayout = itemView.findViewById(R.id.comment_detail_layout);
            detailAvatar = itemView.findViewById(R.id.detail_avatar);
            detailName = itemView.findViewById(R.id.detail_name);
            detailComment = itemView.findViewById(R.id.detail_comment);
            detailClose = itemView.findViewById(R.id.detail_close);
            detailScroll = itemView.findViewById(R.id.detail_scroll);
            
            // 设置ScrollView的触摸事件，确保可以正常滚动
            if (detailScroll != null) {
                detailScroll.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, android.view.MotionEvent event) {
                        // 当在ScrollView内滚动时，阻止RecyclerView拦截触摸事件
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                        // 返回false让ScrollView自己处理滚动
                        return false;
                    }
                });
                
                // 启用嵌套滚动
                detailScroll.setNestedScrollingEnabled(true);
            }
        }
    }
}


