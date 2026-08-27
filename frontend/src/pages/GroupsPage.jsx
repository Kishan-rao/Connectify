import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import NavBar from '../components/NavBar';
import api from '../api/axiosClient';
import '../styles/groups.css';

export default function GroupsPage() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [type, setType] = useState('PUBLIC');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');

  const loadGroups = async () => {
    setLoading(true);
    try {
      const { data } = await api.get('/api/groups?page=0&size=50');
      setGroups(data.content || []);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadGroups(); }, []);

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
      } else {
        await api.post(`/api/groups/${group.id}/join`);
      }
      loadGroups();
    } catch (err) {
      console.error(err);
    }
  };

  return (
    <div className="page-layout">
      <NavBar />
      <div className="groups-container">
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
        <h2 style={{ fontSize: '1.2rem', color: 'var(--text-primary)' }}>Explore Groups ({groups.length})</h2>

        {loading && <div className="loading-spinner">Loading groups...</div>}

        {!loading && groups.length === 0 && (
          <div className="empty-state">
            <span>👥</span>
            <p>No groups created yet. Be the first to start a group!</p>
          </div>
        )}

        <div className="group-grid">
          {groups.map(g => (
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
                <p className="group-card-desc">{g.description || 'No description provided.'}</p>
              </div>

              <div>
                <div className="group-meta" style={{ marginBottom: '0.75rem' }}>
                  <span>👥 {g.memberCount} {g.memberCount === 1 ? 'member' : 'members'}</span>
                  {g.createdBy && <span>by @{g.createdBy.username}</span>}
                </div>
                <div style={{ display: 'flex', gap: '0.5rem' }}>
                  <Link to={`/groups/${g.id}`} className="btn-primary" style={{ flex: 1, textAlign: 'center', padding: '6px 10px', fontSize: '0.85rem' }}>
                    View
                  </Link>
                  <button
                    className={g.isMember ? 'btn-leave' : 'btn-join'}
                    onClick={() => handleToggleMembership(g)}
                  >
                    {g.isMember ? 'Leave' : 'Join'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
