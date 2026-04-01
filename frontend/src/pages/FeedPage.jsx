import { useState, useEffect, useCallback } from 'react';
import NavBar from '../components/NavBar';
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

function PostCard({ post }) {
  const [explanationOpen, setExplanationOpen] = useState(false);

  return (
    <div className="post-card">
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

        {posts.map(post => <PostCard key={post.id} post={post} />)}

        {hasMore && !loading && (
          <button className="btn-load-more" onClick={() => loadFeed(false)}>
            Load More
          </button>
        )}
      </div>
    </div>
  );
}
