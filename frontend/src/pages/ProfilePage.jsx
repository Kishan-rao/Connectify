import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import NavBar from '../components/NavBar';
import { useAuth } from '../context/AuthContext';
import api from '../api/axiosClient';
import '../styles/profile.css';

function PostCard({ post }) {
  return (
    <div className="post-card">
      <div className="post-header">
        <div className="avatar sm">{post.author.username[0].toUpperCase()}</div>
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
    </div>
  );
}

export default function ProfilePage() {
  const { username } = useParams();
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const loadProfile = async () => {
      setLoading(true);
      try {
        const [profileRes, postsRes] = await Promise.all([
          api.get(`/api/users/${username}`),
          api.get(`/api/posts/user/${username}?page=0&size=20`),
        ]);
        setProfile(profileRes.data);
        setPosts(postsRes.data.content);
      } catch (err) {
        console.error(err);
      } finally {
        setLoading(false);
      }
    };
    loadProfile();
  }, [username]);

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
            <div className="stat">
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
        {posts.length === 0 && <div className="empty-state"><p>No posts yet.</p></div>}
        {posts.map(post => <PostCard key={post.id} post={post} />)}
      </div>
    </div>
  );
}
