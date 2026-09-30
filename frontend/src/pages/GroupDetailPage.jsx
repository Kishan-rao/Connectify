import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import NavBar from '../components/NavBar';
import api from '../api/axiosClient';
import '../styles/groups.css';
import '../styles/feed.css';

export default function GroupDetailPage() {
  const { id } = useParams();

  const [group, setGroup] = useState(null);
  const [members, setMembers] = useState([]);
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [postContent, setPostContent] = useState('');
  const [posting, setPosting] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState({ message: '', type: '' });

  // Invite states
  const [showInvite, setShowInvite] = useState(false);
  const [inviteUsername, setInviteUsername] = useState('');
  const [inviting, setInviting] = useState(false);
  const [userSuggestions, setUserSuggestions] = useState([]);
  const [searchingUsers, setSearchingUsers] = useState(false);
  const searchTimeoutRef = useRef(null);

  const showToast = (message, type = 'success') => {
    setToast({ message, type });
    setTimeout(() => {
      setToast({ message: '', type: '' });
    }, 3500);
  };

  const loadGroupDetails = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const groupRes = await api.get(`/api/groups/${id}`);
      setGroup(groupRes.data);

      const isPriv = groupRes.data.type === 'PRIVATE' || groupRes.data.type === 'CLOSED';
      if (groupRes.data.isMember || !isPriv) {
        const [membersRes, postsRes] = await Promise.all([
          api.get(`/api/groups/${id}/members`).catch(() => ({ data: [] })),
          api.get(`/api/groups/${id}/posts?page=0&size=20`).catch(() => ({ data: { content: [] } }))
        ]);
        setMembers(membersRes.data || []);
        setPosts(postsRes.data?.content || []);
      } else {
        setMembers([]);
        setPosts([]);
      }
    } catch (err) {
      if (err.response?.status === 403) {
        setError('This is a private group. You do not have permission to view its details.');
      } else if (err.response?.status === 404) {
        setError('Group not found.');
      } else {
        setError(err.response?.data?.message || 'Failed to load group details.');
      }
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadGroupDetails();
  }, [loadGroupDetails]);

  const handleToggleMembership = async () => {
    try {
      if (group.isMember) {
        await api.delete(`/api/groups/${id}/leave`);
        showToast('You have left the group.');
      } else {
        await api.post(`/api/groups/${id}/join`);
        showToast('Successfully joined the group!');
      }
      loadGroupDetails();
    } catch (err) {
      const errMsg = err.response?.data?.message || 'Failed to update membership.';
      showToast(errMsg, 'error');
    }
  };

  const handleCreatePost = async (e) => {
    e.preventDefault();
    if (!postContent.trim()) return;
    setPosting(true);
    try {
      await api.post('/api/posts', {
        content: postContent.trim(),
        groupId: id
      });
      setPostContent('');
      showToast('Post created successfully!');
      loadGroupDetails();
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to create group post.', 'error');
    } finally {
      setPosting(false);
    }
  };

  // Search users for invite autocomplete
  const handleInviteInputChange = (val) => {
    setInviteUsername(val);
    if (searchTimeoutRef.current) clearTimeout(searchTimeoutRef.current);

    const trimmed = val.trim();
    if (!trimmed) {
      setUserSuggestions([]);
      return;
    }

    searchTimeoutRef.current = setTimeout(async () => {
      setSearchingUsers(true);
      try {
        const { data } = await api.get(`/api/users/search?q=${encodeURIComponent(trimmed)}&page=0&size=5`);
        setUserSuggestions(data.content || []);
      } catch {
        setUserSuggestions([]);
      } finally {
        setSearchingUsers(false);
      }
    }, 300);
  };

  const handleSendInvite = async (e) => {
    if (e) e.preventDefault();
    const targetUser = inviteUsername.trim();
    if (!targetUser || inviting) return;

    setInviting(true);
    try {
      await api.post(`/api/groups/${id}/invite`, { username: targetUser });
      showToast(`Invitation sent to @${targetUser}!`);
      setInviteUsername('');
      setUserSuggestions([]);
      setShowInvite(false);
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to send invitation.', 'error');
    } finally {
      setInviting(false);
    }
  };

  if (loading && !group) {
    return (
      <div className="page-layout">
        <NavBar />
        <div className="loading-spinner">Loading group...</div>
      </div>
    );
  }

  if (!group) {
    return (
      <div className="page-layout">
        <NavBar />
        <div className="groups-container">
          <div className="empty-state">
            <span>🔒</span>
            <p>{error || 'Group not found.'}</p>
          </div>
        </div>
      </div>
    );
  }

  const isPrivate = group.type === 'PRIVATE' || group.type === 'CLOSED';
  const canViewContent = !isPrivate || group.isMember;

  return (
    <div className="page-layout">
      <NavBar />
      <div className="groups-container">

        {/* Toast Banner */}
        {toast.message && (
          <div className={`toast-message ${toast.type}`}>
            <span>{toast.type === 'error' ? '⚠️' : '✓'}</span>
            <span>{toast.message}</span>
          </div>
        )}

        {/* Group Header */}
        <div className="group-header-card">
          <div className="group-title-row">
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <h1 className="group-name">{group.name}</h1>
                <span className={`group-type-badge ${group.type.toLowerCase()}`}>
                  {group.type}
                </span>
              </div>
              {group.description && (
                <p className="group-card-desc" style={{ marginTop: '0.5rem' }}>
                  {group.description}
                </p>
              )}
            </div>

            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
              {group.isMember ? (
                <>
                  <button
                    className="btn-invite"
                    onClick={() => setShowInvite(prev => !prev)}
                  >
                    {showInvite ? 'Cancel Invite' : '+ Invite Member'}
                  </button>
                  <button
                    className="btn-leave"
                    onClick={handleToggleMembership}
                  >
                    Leave Group
                  </button>
                </>
              ) : isPrivate ? (
                <span className="group-invite-only-tag">
                  🔒 Invite Only
                </span>
              ) : (
                <button
                  className="btn-join"
                  onClick={handleToggleMembership}
                >
                  Join Group
                </button>
              )}
            </div>
          </div>

          <div className="group-meta">
            {group.memberCount != null ? (
              <span>👥 {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}</span>
            ) : (
              <span>🔒 Member details hidden</span>
            )}
            {group.createdBy && <span>Created by @{group.createdBy.username}</span>}
          </div>

          {/* Inline Invite Panel for Members */}
          {group.isMember && showInvite && (
            <div className="invite-panel">
              <h3 style={{ fontSize: '0.95rem', color: 'var(--text-primary)' }}>
                Invite a User to {group.name}
              </h3>
              <form onSubmit={handleSendInvite}>
                <div className="invite-input-row">
                  <input
                    type="text"
                    className="search-input"
                    placeholder="Search or enter username..."
                    value={inviteUsername}
                    onChange={e => handleInviteInputChange(e.target.value)}
                    autoFocus
                    maxLength={50}
                  />
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={inviting || !inviteUsername.trim()}
                  >
                    {inviting ? 'Inviting...' : 'Send Invite'}
                  </button>
                </div>
              </form>

              {/* Suggestions Dropdown */}
              {userSuggestions.length > 0 && (
                <div className="user-search-dropdown">
                  {userSuggestions.map(u => (
                    <div
                      key={u.id}
                      className="user-search-item"
                      onClick={() => {
                        setInviteUsername(u.username);
                        setUserSuggestions([]);
                      }}
                    >
                      <div className="avatar sm" style={{ width: '24px', height: '24px', fontSize: '0.75rem' }}>
                        {u.username[0].toUpperCase()}
                      </div>
                      <span>@{u.username}</span>
                    </div>
                  ))}
                </div>
              )}
              {searchingUsers && (
                <span style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Searching users...</span>
              )}
            </div>
          )}
        </div>

        {/* Private Gate for Non-Members */}
        {!canViewContent ? (
          <div
            className="empty-state"
            style={{
              background: 'rgba(24, 24, 38, 0.8)',
              borderRadius: 'var(--radius)',
              border: '1px solid rgba(71, 71, 84, 0.35)',
              padding: '2.5rem 1.5rem'
            }}
          >
            <span style={{ fontSize: '2rem' }}>🔒</span>
            <h3 style={{ marginBottom: '0.5rem', color: 'var(--text-primary)' }}>This group is private</h3>
            <p style={{ color: 'var(--text-secondary)', maxWidth: '420px', margin: '0 auto' }}>
              Only approved members can view group posts and member activity.
              An existing group member must send you an invitation to join.
            </p>
          </div>
        ) : (
          <>
            {/* Create Post in Group (Members only) */}
            {group.isMember && (
              <div className="create-post-card">
                <form onSubmit={handleCreatePost}>
                  <textarea
                    placeholder={`Post something in ${group.name}...`}
                    value={postContent}
                    onChange={e => setPostContent(e.target.value)}
                    maxLength={500}
                    rows={3}
                  />
                  <div className="post-actions">
                    <span className="char-count">{postContent.length} / 500</span>
                    <button type="submit" className="btn-primary" disabled={posting || !postContent.trim()}>
                      {posting ? 'Posting...' : 'Post in Group'}
                    </button>
                  </div>
                </form>
              </div>
            )}

            {/* Group Posts */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <h3 style={{ fontSize: '1.1rem', color: 'var(--text-primary)' }}>
                Group Posts ({posts.length})
              </h3>

              {posts.length === 0 ? (
                <div className="empty-state">
                  <span>📝</span>
                  <p>No posts in this group yet. Start the conversation!</p>
                </div>
              ) : (
                posts.map(p => (
                  <div key={p.id} className="post-card">
                    <div className="post-header">
                      <div className="avatar">{p.author.username[0].toUpperCase()}</div>
                      <div>
                        <span className="post-author">@{p.author.username}</span>
                        <span className="post-time">
                          {new Date(p.createdAt).toLocaleDateString('en-US', {
                            month: 'short',
                            day: 'numeric',
                            hour: '2-digit',
                            minute: '2-digit'
                          })}
                        </span>
                      </div>
                    </div>
                    <p className="post-content">{p.content}</p>
                    <div className="post-footer-actions">
                      <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                        ❤️ {p.likeCount} {p.likeCount === 1 ? 'Like' : 'Likes'}
                      </span>
                      <span style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
                        💬 {p.commentCount} {p.commentCount === 1 ? 'Comment' : 'Comments'}
                      </span>
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Members Section */}
            {members.length > 0 && (
              <div className="group-header-card" style={{ marginTop: '1rem' }}>
                <h3 style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>
                  Members ({members.length})
                </h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
                  {members.map(m => (
                    <Link
                      key={m.id}
                      to={`/profile/${m.username}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '0.4rem',
                        background: 'rgba(13, 13, 24, 0.6)',
                        padding: '4px 10px',
                        borderRadius: '20px',
                        fontSize: '0.85rem',
                        color: 'var(--text-primary)'
                      }}
                    >
                      <div className="avatar sm" style={{ width: '22px', height: '22px', fontSize: '0.7rem' }}>
                        {m.username[0].toUpperCase()}
                      </div>
                      @{m.username}
                    </Link>
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
