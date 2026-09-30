import { useState } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axiosClient';

function FeedExplanationPanel({ explanation, isOpen, onClose }) {
  if (!explanation) return null;
  return (
    <div className={`feed-explanation-panel ${isOpen ? 'open' : ''}`}>
      <div className="feed-explanation-inner">
        <span className="feed-explanation-icon">💡</span>
        <p className="feed-explanation-text">{explanation}</p>
        <button className="feed-explanation-close" onClick={onClose} aria-label="Close explanation">×</button>
      </div>
    </div>
  );
}

export default function PostCard({ post, currentUsername, onPostDeleted }) {
  const [explanationOpen, setExplanationOpen] = useState(false);
  const [liked, setLiked] = useState(post.likedByCurrentUser || false);
  const [likeCount, setLikeCount] = useState(post.likeCount || 0);
  const [commentCount, setCommentCount] = useState(post.commentCount || 0);

  const [showComments, setShowComments] = useState(false);
  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');
  const [commentLoading, setCommentLoading] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const handleToggleLike = async () => {
    try {
      if (liked) {
        await api.delete(`/api/posts/${post.id}/like`);
        setLiked(false);
        setLikeCount(c => Math.max(0, c - 1));
      } else {
        await api.post(`/api/posts/${post.id}/like`);
        setLiked(true);
        setLikeCount(c => c + 1);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const loadComments = async () => {
    try {
      const { data } = await api.get(`/api/posts/${post.id}/comments`);
      setComments(data || []);
    } catch (err) {
      console.error(err);
    }
  };

  const toggleComments = () => {
    if (!showComments) {
      loadComments();
    }
    setShowComments(prev => !prev);
  };

  const handleAddComment = async (e) => {
    e.preventDefault();
    if (!commentText.trim() || commentLoading) return;
    setCommentLoading(true);
    try {
      const { data } = await api.post(`/api/posts/${post.id}/comments`, { content: commentText.trim() });
      setComments(prev => [...prev, data]);
      setCommentCount(c => c + 1);
      setCommentText('');
    } catch (err) {
      console.error(err);
    } finally {
      setCommentLoading(false);
    }
  };

  const handleDeleteComment = async (commentId) => {
    try {
      await api.delete(`/api/comments/${commentId}`);
      setComments(prev => prev.filter(c => c.id !== commentId));
      setCommentCount(c => Math.max(0, c - 1));
    } catch (err) {
      console.error(err);
    }
  };

  const handleDeletePost = async () => {
    if (!window.confirm('Are you sure you want to delete this post?')) return;
    setDeleting(true);
    try {
      await api.delete(`/api/posts/${post.id}`);
      if (onPostDeleted) onPostDeleted(post.id);
    } catch (err) {
      console.error(err);
      setDeleting(false);
    }
  };

  const isAuthor = post.author?.username === currentUsername;

  return (
    <div className="post-card">
      {post.group && (
        <span className="post-group-tag">
          👥 {post.group.name}
        </span>
      )}

      <div className="post-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <Link to={`/profile/${post.author?.username}`} style={{ textDecoration: 'none' }}>
            <div className="avatar">{post.author?.username?.[0]?.toUpperCase() || '?'}</div>
          </Link>
          <div>
            <Link
              to={`/profile/${post.author?.username}`}
              className="post-author"
              style={{ fontWeight: 600, color: 'var(--text-primary)', textDecoration: 'none' }}
            >
              @{post.author?.username}
            </Link>
            <span className="post-time" style={{ display: 'block', fontSize: '0.78rem', color: 'var(--text-muted)' }}>
              {new Date(post.createdAt).toLocaleDateString('en-US', {
                month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
              })}
            </span>
          </div>
        </div>

        {isAuthor && (
          <button
            onClick={handleDeletePost}
            disabled={deleting}
            title="Delete post"
            style={{
              background: 'transparent',
              border: 'none',
              color: 'var(--text-muted)',
              cursor: 'pointer',
              fontSize: '0.9rem',
              padding: '4px 8px',
              borderRadius: '4px'
            }}
          >
            {deleting ? '...' : '🗑️'}
          </button>
        )}
      </div>

      <p className="post-content">{post.content}</p>

      {post.imageUrl && (
        <img
          src={post.imageUrl}
          alt="Post media"
          style={{ width: '100%', borderRadius: '8px', marginTop: '0.5rem' }}
        />
      )}

      {/* Like and Comment Actions */}
      <div className="post-footer-actions">
        <button
          className={`interaction-btn ${liked ? 'liked' : ''}`}
          onClick={handleToggleLike}
        >
          {liked ? '❤️' : '🤍'} {likeCount} {likeCount === 1 ? 'Like' : 'Likes'}
        </button>

        <button
          className="interaction-btn"
          onClick={toggleComments}
        >
          💬 {commentCount} {commentCount === 1 ? 'Comment' : 'Comments'}
        </button>
      </div>

      {/* Comments Drawer */}
      {showComments && (
        <div className="comments-drawer">
          <form onSubmit={handleAddComment} className="add-comment-form">
            <input
              type="text"
              placeholder="Write a comment..."
              value={commentText}
              maxLength={500}
              onChange={e => setCommentText(e.target.value)}
              className="add-comment-input"
            />
            <button
              type="submit"
              className="btn-primary"
              style={{ padding: '6px 12px', fontSize: '0.8rem' }}
              disabled={commentLoading || !commentText.trim()}
            >
              {commentLoading ? '...' : 'Send'}
            </button>
          </form>

          <div className="comment-list">
            {comments.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.82rem', padding: '4px 0' }}>
                No comments yet. Be the first to comment!
              </p>
            ) : (
              comments.map(c => (
                <div key={c.id} className="comment-item">
                  <div>
                    <Link
                      to={`/profile/${c.user?.username}`}
                      className="comment-author"
                      style={{ fontWeight: 600, color: 'var(--text-primary)', textDecoration: 'none' }}
                    >
                      @{c.user?.username}
                    </Link>
                    <p className="comment-text">{c.content}</p>
                  </div>
                  {c.user?.username === currentUsername && (
                    <button
                      className="comment-delete-btn"
                      onClick={() => handleDeleteComment(c.id)}
                      title="Delete comment"
                    >
                      ✕
                    </button>
                  )}
                </div>
              ))
            )}
          </div>
        </div>
      )}

      {post.feedExplanation && (
        <>
          <div className="post-card-divider" />
          <button
            className="why-seeing-this-btn"
            onClick={() => setExplanationOpen(prev => !prev)}
            aria-expanded={explanationOpen}
          >
            ✨ Why am I seeing this?
          </button>
          <FeedExplanationPanel
            explanation={post.feedExplanation}
            isOpen={explanationOpen}
            onClose={() => setExplanationOpen(false)}
          />
        </>
      )}
    </div>
  );
}
