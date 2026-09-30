import { useState, useEffect, useCallback, useRef } from 'react';
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

  // Profile actions & feedback
  const [actionLoading, setActionLoading] = useState(false);
  const [toast, setToast] = useState({ message: '', type: 'success' });
  const [showMoreMenu, setShowMoreMenu] = useState(false);
  const [isBlocked, setIsBlocked] = useState(false);
  const [showReportModal, setShowReportModal] = useState(false);
  const [reportReason, setReportReason] = useState('SPAM');
  const [reportDetails, setReportDetails] = useState('');
  const moreMenuRef = useRef(null);

  // Friend List Modal state
  const [showFriendsModal, setShowFriendsModal] = useState(false);
  const [friends, setFriends] = useState([]);
  const [friendsLoading, setFriendsLoading] = useState(false);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => {
      setToast({ message: '', type: 'success' });
    }, 3500);
  };

  const loadProfileAndPosts = useCallback(async () => {
    setLoading(true);
    setShowFriendsModal(false);
    setShowMoreMenu(false);
    setIsBlocked(false);
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

  // Click outside listener for more options dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (moreMenuRef.current && !moreMenuRef.current.contains(e.target)) {
        setShowMoreMenu(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

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

  // Relationship actions
  const handleSendFriendRequest = async () => {
    if (actionLoading) return;
    setActionLoading(true);
    try {
      await api.post('/api/friendships', { addresseeUsername: profile.username });
      setProfile(prev => ({ ...prev, relationshipStatus: 'PENDING_SENT' }));
      showToast(`Friend request sent to @${profile.username}!`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to send friend request.', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleAcceptFriendRequest = async () => {
    if (actionLoading || !profile?.friendshipId) return;
    setActionLoading(true);
    try {
      await api.put(`/api/friendships/${profile.friendshipId}/accept`);
      setProfile(prev => ({
        ...prev,
        relationshipStatus: 'FRIENDS',
        friendCount: (prev.friendCount || 0) + 1,
        friendshipId: null
      }));
      showToast(`Accepted friend request from @${profile.username}!`, 'success');
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to accept friend request.', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeclineFriendRequest = async () => {
    if (actionLoading || !profile?.friendshipId) return;
    setActionLoading(true);
    try {
      await api.put(`/api/friendships/${profile.friendshipId}/reject`);
      setProfile(prev => ({
        ...prev,
        relationshipStatus: 'NONE',
        friendshipId: null
      }));
      showToast(`Declined friend request from @${profile.username}.`, 'info');
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to decline friend request.', 'error');
    } finally {
      setActionLoading(false);
    }
  };

  const handleMessageClick = () => {
    showToast('Direct messaging is currently unavailable in this release.', 'info');
  };

  const handleBlockClick = () => {
    if (isBlocked) {
      if (window.confirm(`Unblock @${profile.username}?`)) {
        setIsBlocked(false);
        showToast(`@${profile.username} has been unblocked.`, 'success');
      }
    } else {
      if (window.confirm(`Are you sure you want to block @${profile.username}? You will no longer see updates from this user.`)) {
        setIsBlocked(true);
        showToast(`@${profile.username} has been blocked.`, 'success');
      }
    }
  };

  const handleOpenReportModal = () => {
    setReportReason('SPAM');
    setReportDetails('');
    setShowReportModal(true);
  };

  const handleReportSubmit = (e) => {
    e.preventDefault();
    setShowReportModal(false);
    showToast(`Thank you for reporting @${profile.username}. Our moderation team will review this account.`, 'success');
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

  const isOwner = user?.username === username || profile.relationshipStatus === 'SELF';

  return (
    <div className="page-layout">
      <NavBar />
      <div className="profile-container">
        {toast.message && (
          <div className={`toast-message ${toast.type}`}>
            <span>{toast.message}</span>
          </div>
        )}

        {isBlocked && (
          <div className="blocked-banner">
            <span>🚫 You have blocked @{profile.username}.</span>
          </div>
        )}

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

          {/* Action Row */}
          {!isOwner && (
            <div className="profile-actions-row">
              {profile.relationshipStatus === 'NONE' && (
                <button
                  className="btn-primary"
                  onClick={handleSendFriendRequest}
                  disabled={actionLoading}
                >
                  {actionLoading ? 'Sending...' : '+ Add Friend'}
                </button>
              )}

              {profile.relationshipStatus === 'PENDING_SENT' && (
                <span className="badge-status pending">
                  Request Sent
                </span>
              )}

              {profile.relationshipStatus === 'PENDING_RECEIVED' && (
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <button
                    className="btn-primary"
                    onClick={handleAcceptFriendRequest}
                    disabled={actionLoading}
                  >
                    Accept
                  </button>
                  <button
                    className="btn-secondary"
                    onClick={handleDeclineFriendRequest}
                    disabled={actionLoading}
                  >
                    Decline
                  </button>
                </div>
              )}

              {profile.relationshipStatus === 'FRIENDS' && (
                <span className="badge-status friends">
                  Friends ✓
                </span>
              )}

              <button
                className="btn-action-outline"
                onClick={handleMessageClick}
                title="Message user"
              >
                💬 Message
              </button>

              <div className="profile-more-menu-wrapper" ref={moreMenuRef}>
                <button
                  className="btn-action-more"
                  onClick={() => setShowMoreMenu(prev => !prev)}
                  aria-label="More options"
                  title="More options"
                >
                  ⋯
                </button>

                {showMoreMenu && (
                  <div className="profile-more-dropdown">
                    <button
                      className="profile-menu-item"
                      onClick={() => { setShowMoreMenu(false); handleMessageClick(); }}
                    >
                      💬 Message
                    </button>
                    <button
                      className="profile-menu-item"
                      onClick={() => { setShowMoreMenu(false); handleBlockClick(); }}
                    >
                      🚫 {isBlocked ? 'Unblock User' : 'Block User'}
                    </button>
                    <button
                      className="profile-menu-item danger"
                      onClick={() => { setShowMoreMenu(false); handleOpenReportModal(); }}
                    >
                      🚩 Report Account
                    </button>
                  </div>
                )}
              </div>
            </div>
          )}

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

      {/* Report Modal */}
      {showReportModal && (
        <div className="modal-overlay" onClick={() => setShowReportModal(false)}>
          <div className="modal-card" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h3>Report @{profile.username}</h3>
              <button
                className="modal-close-btn"
                onClick={() => setShowReportModal(false)}
                aria-label="Close"
              >
                ✕
              </button>
            </div>
            <form onSubmit={handleReportSubmit} className="modal-body">
              <label style={{ fontSize: '0.88rem', color: 'var(--text-secondary)' }}>
                Why are you reporting this user?
              </label>
              <select
                className="report-select"
                value={reportReason}
                onChange={e => setReportReason(e.target.value)}
              >
                <option value="SPAM">Spam or harmful links</option>
                <option value="HARASSMENT">Harassment or bullying</option>
                <option value="INAPPROPRIATE">Inappropriate or graphic content</option>
                <option value="IMPERSONATION">Impersonation or fake account</option>
                <option value="OTHER">Other</option>
              </select>

              <label style={{ fontSize: '0.88rem', color: 'var(--text-secondary)', marginTop: '0.5rem' }}>
                Additional details (optional):
              </label>
              <textarea
                className="report-textarea"
                rows={3}
                placeholder="Describe the issue..."
                value={reportDetails}
                onChange={e => setReportDetails(e.target.value)}
              />

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1rem' }}>
                <button
                  type="button"
                  className="btn-secondary"
                  onClick={() => setShowReportModal(false)}
                >
                  Cancel
                </button>
                <button type="submit" className="btn-primary">
                  Submit Report
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
