import { useState, useEffect, useCallback } from 'react';
import NavBar from '../components/NavBar';
import { useAuth } from '../context/AuthContext';
import api from '../api/axiosClient';
import '../styles/feed.css';

function CreatePostForm({ onPostCreated }) {
  const [content, setContent] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!content.trim()) return;
    setLoading(true);
    try {
      await api.post('/api/posts', { content });
      setContent('');
      onPostCreated();
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="create-post-card">
      <form onSubmit={handleSubmit}>
        <textarea
          placeholder="What's on your mind?"
          value={content}
          onChange={e => setContent(e.target.value)}
          rows={3}
        />
        <div className="post-actions">
          <span className="char-count">{content.length} / 500</span>
          <button type="submit" className="btn-primary" disabled={loading || !content.trim()}>
            {loading ? 'Posting...' : 'Post'}
          </button>
        </div>
      </form>
    </div>
  );
}

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

function PostCard({ post, currentUsername, onPostUpdated }) {
  const [explanationOpen, setExplanationOpen] = useState(false);
  const [liked, setLiked] = useState(post.likedByCurrentUser || false);
  const [likeCount, setLikeCount] = useState(post.likeCount || 0);
  const [commentCount, setCommentCount] = useState(post.commentCount || 0);

  const [showComments, setShowComments] = useState(false);
  const [comments, setComments] = useState([]);
  const [commentText, setCommentText] = useState('');
  const [commentLoading, setCommentLoading] = useState(false);

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
      setComments(data);
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
      const { data } = await api.post(`/api/posts/${post.id}/comments`, { content: commentText });
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

  return (
    <div className="post-card">
      {post.group && (
        <span className="post-group-tag">
          👥 {post.group.name}
        </span>
      )}

      <div className="post-header">
        <div className="avatar">{post.author.username[0].toUpperCase()}</div>
        <div>
          <span className="post-author">@{post.author.username}</span>
          <span className="post-time">
            {new Date(post.createdAt).toLocaleDateString('en-US', {
              month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
            })}
          </span>
        </div>
      </div>

      <p className="post-content">{post.content}</p>

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

      {/* Comments Section */}
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
                    <span className="comment-author">@{c.user.username}</span>
                    <p className="comment-text">{c.content}</p>
                  </div>
                  {c.user.username === currentUsername && (
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

export default function FeedPage() {
  const { user } = useAuth();
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);

  const loadFeed = useCallback(async (reset = false) => {
    setLoading(true);
    try {
      const currentPage = reset ? 0 : page;
      const { data } = await api.get(`/api/feed?page=${currentPage}&size=20`);
      setPosts(prev => reset ? data.content : [...prev, ...data.content]);
      setHasMore(currentPage + 1 < data.totalPages);
      if (!reset) setPage(p => p + 1);
      else setPage(1);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { loadFeed(true); }, []);

  return (
    <div className="page-layout">
      <NavBar />
      <div className="feed-container">
        <CreatePostForm onPostCreated={() => loadFeed(true)} />

        {loading && posts.length === 0 && (
          <div className="loading-spinner">Loading feed...</div>
        )}

        {!loading && posts.length === 0 && (
          <div className="empty-state">
            <span>🌟</span>
            <p>Your feed is empty! Add some friends and start posting.</p>
          </div>
        )}

        {posts.map(post => (
          <PostCard
            key={post.id}
            post={post}
            currentUsername={user?.username}
            onPostUpdated={() => loadFeed(true)}
          />
        ))}

        {hasMore && !loading && (
          <button className="btn-load-more" onClick={() => loadFeed(false)}>
            Load More
          </button>
        )}
      </div>
    </div>
  );
}

