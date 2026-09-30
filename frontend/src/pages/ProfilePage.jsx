import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import NavBar from '../components/NavBar';
import PostCard from '../components/PostCard';
import { useAuth } from '../context/AuthContext';
import api from '../api/axiosClient';
import '../styles/profile.css';
import '../styles/feed.css';

export default function ProfilePage() {
  const { username } = useParams();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [postsLoading, setPostsLoading] = useState(false);
  const [postsPage, setPostsPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [deleting, setDeleting] = useState(false);

  // Friend List Modal state
  const [showFriendsModal, setShowFriendsModal] = useState(false);
  const [friends, setFriends] = useState([]);
  const [friendsLoading, setFriendsLoading] = useState(false);

  const loadProfileAndPosts = useCallback(async () => {
    setLoading(true);
    setShowFriendsModal(false);
    try {
      const [profileRes, postsRes] = await Promise.all([
        api.get(`/api/users/${username}`),
        api.get(`/api/posts/user/${username}?page=0&size=20`),
      ]);
      setProfile(profileRes.data);
      setPosts(postsRes.data.content || []);
      setTotalPages(postsRes.data.totalPages || 0);
      setPostsPage(0);
    } catch (err) {
      console.error(err);
      setProfile(null);
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => {
    loadProfileAndPosts();
  }, [loadProfileAndPosts]);

  const loadMorePosts = async () => {
    if (postsLoading || postsPage + 1 >= totalPages) return;
    setPostsLoading(true);
    try {
      const nextPage = postsPage + 1;
      const { data } = await api.get(`/api/posts/user/${username}?page=${nextPage}&size=20`);
      setPosts(prev => [...prev, ...(data.content || [])]);
      setPostsPage(nextPage);
    } catch (err) {
      console.error(err);
    } finally {
      setPostsLoading(false);
    }
  };

  const handlePostDeleted = (deletedPostId) => {
    setPosts(prev => prev.filter(p => p.id !== deletedPostId));
    setProfile(prev => prev ? { ...prev, postCount: Math.max(0, prev.postCount - 1) } : prev);
  };

  const handleOpenFriendsModal = async () => {
    setShowFriendsModal(true);
    setFriendsLoading(true);
    try {
      const { data } = await api.get(`/api/users/${username}/friends`);
      setFriends(data || []);
    } catch (err) {
      console.error(err);
      setFriends([]);
    } finally {
      setFriendsLoading(false);
    }
  };

  const handleDeleteAccount = async () => {
    if (!window.confirm('Are you sure you want to permanently delete your account? All your posts, comments, likes, and profile data will be permanently removed.')) {
      return;
    }
    setDeleting(true);
    try {
      await api.delete('/api/auth/account');
      logout();
      navigate('/login');
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to delete account.');
      setDeleting(false);
    }
  };

  if (loading) return (
    <div className="page-layout">
      <NavBar />
      <div className="loading-spinner">Loading profile...</div>
    </div>
  );

  if (!profile) return (
    <div className="page-layout">
      <NavBar />
      <div className="empty-state"><p>User not found.</p></div>
    </div>
  );

  const isOwner = user?.username === username;

  return (
    <div className="page-layout">
      <NavBar />
      <div className="profile-container">
        <div className="profile-header-card">
          <div className="profile-avatar">{profile.username[0].toUpperCase()}</div>
          <div className="profile-info">
            <h2>@{profile.username}</h2>
            <p className="joined-date">
              Joined {new Date(profile.createdAt).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })}
            </p>
          </div>
          <div className="profile-stats">
            <div
              className="stat clickable"
              onClick={handleOpenFriendsModal}
              role="button"
              tabIndex={0}
              title="View friends"
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') handleOpenFriendsModal(); }}
            >
              <span className="stat-value">{profile.friendCount}</span>
              <span className="stat-label">Friends</span>
            </div>
            <div className="stat">
              <span className="stat-value">{profile.postCount}</span>
              <span className="stat-label">Posts</span>
            </div>
          </div>
          {isOwner && (
            <div style={{ marginTop: '0.75rem', width: '100%', display: 'flex', justifyContent: 'flex-end' }}>
              <button
                className="btn-danger"
                style={{
                  background: 'rgba(239, 68, 68, 0.15)',
                  border: '1px solid rgba(239, 68, 68, 0.4)',
                  color: '#ef4444',
                  padding: '6px 12px',
                  borderRadius: '8px',
                  cursor: 'pointer',
                  fontSize: '0.8rem',
                  fontWeight: 600
                }}
                onClick={handleDeleteAccount}
                disabled={deleting}
              >
                {deleting ? 'Deleting Account...' : '🗑️ Delete Account'}
              </button>
            </div>
          )}
        </div>

        <h3 className="posts-heading">Posts</h3>
        {posts.length === 0 && (
          <div className="empty-state">
            <span>📝</span>
            <p>No posts yet.</p>
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

        {postsPage + 1 < totalPages && (
          <button
            className="btn-load-more"
            onClick={loadMorePosts}
            disabled={postsLoading}
            style={{ alignSelf: 'center', marginTop: '0.5rem' }}
          >
            {postsLoading ? 'Loading...' : 'Load More'}
          </button>
        )}
      </div>

      {/* Friends Modal */}
      {showFriendsModal && (
        <div className="modal-overlay" onClick={() => setShowFriendsModal(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>@{profile.username}'s Friends ({profile.friendCount})</h3>
              <button
                className="modal-close-btn"
                onClick={() => setShowFriendsModal(false)}
                aria-label="Close"
              >
                ✕
              </button>
            </div>
            <div className="modal-body">
              {friendsLoading ? (
                <div style={{ textAlign: 'center', padding: '1.5rem', color: 'var(--text-secondary)' }}>
                  Loading friends...
                </div>
              ) : friends.length === 0 ? (
                <div className="empty-state" style={{ padding: '1.5rem' }}>
                  <p>No friends yet.</p>
                </div>
              ) : (
                friends.map(friend => (
                  <Link
                    key={friend.id}
                    to={`/profile/${friend.username}`}
                    className="friend-list-item"
                    onClick={() => setShowFriendsModal(false)}
                  >
                    <div className="avatar sm">{friend.username[0].toUpperCase()}</div>
                    <span className="friend-username">@{friend.username}</span>
                  </Link>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
