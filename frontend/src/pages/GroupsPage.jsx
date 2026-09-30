import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import NavBar from '../components/NavBar';
import api from '../api/axiosClient';
import '../styles/groups.css';

export default function GroupsPage() {
  const [groups, setGroups] = useState([]);
  const [invitations, setInvitations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState('PUBLIC');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [toast, setToast] = useState({ message: '', type: '' });
  const [processingInvId, setProcessingInvId] = useState(null);

  const showToast = (message, toastType = 'success') => {
    setToast({ message, type: toastType });
    setTimeout(() => {
      setToast({ message: '', type: '' });
    }, 3500);
  };

  const loadGroups = useCallback(async () => {
    try {
      const { data } = await api.get('/api/groups?page=0&size=50');
      setGroups(data.content || []);
    } catch (err) {
      console.error(err);
    }
  }, []);

  const loadInvitations = useCallback(async () => {
    try {
      const { data } = await api.get('/api/groups/invitations');
      setInvitations(data || []);
    } catch {
      // ignore
    }
  }, []);

  const loadAll = useCallback(async () => {
    setLoading(true);
    await Promise.all([loadGroups(), loadInvitations()]);
    setLoading(false);
  }, [loadGroups, loadInvitations]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  const handleCreateGroup = async (e) => {
    e.preventDefault();
    if (!name.trim()) return;
    setCreating(true);
    setError('');
    try {
      await api.post('/api/groups', {
        name: name.trim(),
        description: description.trim(),
        type
      });
      setName('');
      setDescription('');
      showToast('Group created successfully!');
      loadGroups();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to create group.');
    } finally {
      setCreating(false);
    }
  };

  const handleToggleMembership = async (group) => {
    try {
      if (group.isMember) {
        await api.delete(`/api/groups/${group.id}/leave`);
        showToast(`Left ${group.name}.`);
      } else {
        await api.post(`/api/groups/${group.id}/join`);
        showToast(`Joined ${group.name}!`);
      }
      loadGroups();
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to update membership.', 'error');
    }
  };

  const handleAcceptInvitation = async (invitation) => {
    setProcessingInvId(invitation.id);
    try {
      await api.post(`/api/groups/invitations/${invitation.id}/accept`);
      showToast(`Accepted invitation to ${invitation.groupName}!`);
      await Promise.all([loadInvitations(), loadGroups()]);
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to accept invitation.', 'error');
    } finally {
      setProcessingInvId(null);
    }
  };

  const handleDeclineInvitation = async (invitation) => {
    setProcessingInvId(invitation.id);
    try {
      await api.post(`/api/groups/invitations/${invitation.id}/decline`);
      showToast(`Declined invitation to ${invitation.groupName}.`);
      await loadInvitations();
    } catch (err) {
      showToast(err.response?.data?.message || 'Failed to decline invitation.', 'error');
    } finally {
      setProcessingInvId(null);
    }
  };

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

        {/* Pending Group Invitations Section */}
        {invitations.length > 0 && (
          <div className="invitations-card">
            <h2>
              <span>📨</span> Pending Group Invitations ({invitations.length})
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
              {invitations.map(inv => (
                <div key={inv.id} className="invitation-row">
                  <div className="invitation-info">
                    <div className="avatar sm" style={{ background: 'var(--accent)', color: '#fff' }}>
                      {inv.groupName[0]?.toUpperCase() || 'G'}
                    </div>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                        <Link
                          to={`/groups/${inv.groupId}`}
                          style={{ fontWeight: 600, color: 'var(--text-primary)', textDecoration: 'none' }}
                        >
                          {inv.groupName}
                        </Link>
                        <span className={`group-type-badge ${inv.groupType?.toLowerCase()}`}>
                          {inv.groupType}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '2px' }}>
                        Invited by @{inv.inviter?.username}
                        {inv.createdAt && (
                          <span> • {new Date(inv.createdAt).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</span>
                        )}
                      </div>
                    </div>
                  </div>

                  <div className="invitation-actions">
                    <button
                      className="btn-accept"
                      disabled={processingInvId === inv.id}
                      onClick={() => handleAcceptInvitation(inv)}
                    >
                      {processingInvId === inv.id ? 'Accepting...' : 'Accept'}
                    </button>
                    <button
                      className="btn-decline"
                      disabled={processingInvId === inv.id}
                      onClick={() => handleDeclineInvitation(inv)}
                    >
                      {processingInvId === inv.id ? 'Declining...' : 'Decline'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Create Group Card */}
        <div className="group-header-card">
          <h2 style={{ fontSize: '1.2rem', color: 'var(--text-primary)' }}>Create a New Group</h2>
          <form onSubmit={handleCreateGroup} style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <input
                type="text"
                className="search-input"
                placeholder="Group Name..."
                value={name}
                onChange={e => setName(e.target.value)}
                required
                maxLength={100}
              />
              <select
                value={type}
                onChange={e => setType(e.target.value)}
                style={{
                  background: 'rgba(13, 13, 24, 0.7)',
                  border: '1px solid rgba(71, 71, 84, 0.5)',
                  borderRadius: '10px',
                  padding: '0 1rem',
                  color: 'var(--text-primary)',
                  outline: 'none'
                }}
              >
                <option value="PUBLIC">Public</option>
                <option value="PRIVATE">Private</option>
                <option value="CLOSED">Closed</option>
              </select>
            </div>
            <textarea
              className="search-input"
              placeholder="Description (optional)..."
              value={description}
              onChange={e => setDescription(e.target.value)}
              rows={2}
              maxLength={1000}
              style={{ resize: 'none' }}
            />
            {error && <p style={{ color: 'var(--danger)', fontSize: '0.85rem' }}>{error}</p>}
            <button
              type="submit"
              className="btn-primary"
              style={{ alignSelf: 'flex-start' }}
              disabled={creating || !name.trim()}
            >
              {creating ? 'Creating...' : 'Create Group'}
            </button>
          </form>
        </div>

        {/* Groups Grid */}
        <h2 style={{ fontSize: '1.2rem', color: 'var(--text-primary)' }}>
          Explore Groups ({groups.length})
        </h2>

        {loading && <div className="loading-spinner">Loading groups...</div>}

        {!loading && groups.length === 0 && (
          <div className="empty-state">
            <span>👥</span>
            <p>No groups created yet. Be the first to start a group!</p>
          </div>
        )}

        <div className="group-grid">
          {groups.map(g => {
            const isPrivate = g.type === 'PRIVATE' || g.type === 'CLOSED';

            return (
              <div key={g.id} className="group-card">
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                    <Link to={`/groups/${g.id}`} style={{ fontWeight: 700, fontSize: '1.05rem', color: 'var(--text-primary)' }}>
                      {g.name}
                    </Link>
                    <span className={`group-type-badge ${g.type.toLowerCase()}`}>
                      {g.type}
                    </span>
                  </div>
                  <p className="group-card-desc">
                    {g.description || (isPrivate && !g.isMember ? 'Private group. Content and members are hidden.' : 'No description provided.')}
                  </p>
                </div>

                <div>
                  <div className="group-meta" style={{ marginBottom: '0.75rem' }}>
                    {g.memberCount != null ? (
                      <span>👥 {g.memberCount} {g.memberCount === 1 ? 'member' : 'members'}</span>
                    ) : (
                      <span>🔒 Members hidden</span>
                    )}
                    {g.createdBy && <span>by @{g.createdBy.username}</span>}
                  </div>
                  <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
                    <Link
                      to={`/groups/${g.id}`}
                      className="btn-primary"
                      style={{ flex: 1, textAlign: 'center', padding: '6px 10px', fontSize: '0.85rem' }}
                    >
                      View
                    </Link>

                    {g.isMember ? (
                      <button
                        className="btn-leave"
                        onClick={() => handleToggleMembership(g)}
                      >
                        Leave
                      </button>
                    ) : isPrivate ? (
                      <span className="group-invite-only-tag" style={{ padding: '6px 10px' }}>
                        Invite Only
                      </span>
                    ) : (
                      <button
                        className="btn-join"
                        onClick={() => handleToggleMembership(g)}
                      >
                        Join
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
