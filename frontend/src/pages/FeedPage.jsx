import { useState, useEffect, useCallback } from 'react';
import NavBar from '../components/NavBar';
import PostCard from '../components/PostCard';
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
          maxLength={500}
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

  // This is intentionally an initial load only; including loadFeed would reload
  // the first page whenever the pagination state changes.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { loadFeed(true); }, []);

  const handlePostDeleted = (deletedPostId) => {
    setPosts(prev => prev.filter(p => p.id !== deletedPostId));
  };

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
            onPostDeleted={handlePostDeleted}
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
