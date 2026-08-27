import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import NavBar from '../components/NavBar';
import { useAuth } from '../context/AuthContext';
import api from '../api/axiosClient';
import '../styles/groups.css';
import '../styles/feed.css';

export default function GroupDetailPage() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();

  const [group, setGroup] = useState(null);
  const [members, setMembers] = useState([]);
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [postContent, setPostContent] = useState('');
  const [posting, setPosting] = useState(false);
  const [error, setError] = useState('');

  const loadGroupDetails = async () => {
    setLoading(true);
    setError('');
    try {
      const groupRes = await api.get(`/api/groups/${id}`);
      setGroup(groupRes.data);

      if (groupRes.data.isMember || groupRes.data.type === 'PUBLIC' || groupRes.data.type === 'OPEN') {
        const [membersRes, postsRes] = await Promise.all([
          api.get(`/api/groups/${id}/members`).catch(() => ({ data: [] })),
          api.get(`/api/groups/${id}/posts?page=0&size=20`).catch(() => ({ data: { content: [] } }))
        ]);
        setMembers(membersRes.data || []);
        setPosts(postsRes.data?.content || []);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to load group details.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadGroupDetails(); }, [id]);

  const handleToggleMembership = async () => {
    try {
      if (group.isMember) {
        await api.delete(`/api/groups/${id}/leave`);
      } else {
        await api.post(`/api/groups/${id}/join`);
      }
      loadGroupDetails();
    } catch (err) {
      console.error(err);
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
      loadGroupDetails();
    } catch (err) {
      alert(err.response?.data?.message || 'Failed to create group post.');
    } finally {
      setPosting(false);
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
        <div className="empty-state">
          <span>❌</span>
          <p>{error || 'Group not found.'}</p>
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
        {/* Header */}
        <div className="group-header-card">
          <div className="group-title-row">
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <h1 className="group-name">{group.name}</h1>
                <span className={`group-type-badge ${group.type.toLowerCase()}`}>
                  {group.type}
                </span>
              </div>
              <p className="group-card-desc" style={{ marginTop: '0.5rem' }}>{group.description}</p>
            </div>
            <button
              className={group.isMember ? 'btn-leave' : 'btn-join'}
              onClick={handleToggleMembership}
            >
              {group.isMember ? 'Leave Group' : 'Join Group'}
            </button>
          </div>

          <div className="group-meta">
            <span>👥 {group.memberCount} {group.memberCount === 1 ? 'member' : 'members'}</span>
            {group.createdBy && <span>Created by @{group.createdBy.username}</span>}
          </div>
        </div>

        {/* Private Gate */}
        {!canViewContent ? (
          <div className="empty-state" style={{ background: 'rgba(24, 24, 38, 0.8)', borderRadius: 'var(--radius)', border: '1px solid rgba(71, 71, 84, 0.35)' }}>
            <span>🔒</span>
            <h3 style={{ marginBottom: '0.5rem' }}>This group is private</h3>
            <p>Join this group to participate in discussions and view member posts.</p>
          </div>
        ) : (
          <>
            {/* Create Post in Group */}
            {group.isMember && (
              <div className="create-post-card">
                <form onSubmit={handleCreatePost}>
                  <textarea
                    placeholder={`Post something in ${group.name}...`}
                    value={postContent}
                    onChange={e => setPostContent(e.target.value)}
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

            {/* Posts */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
              <h3 style={{ fontSize: '1.1rem', color: 'var(--text-primary)' }}>Group Posts ({posts.length})</h3>

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
                            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
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
                <h3 style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>Members ({members.length})</h3>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '0.75rem' }}>
                  {members.map(m => (
                    <a
                      key={m.id}
                      href={`/profile/${m.username}`}
                      style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', background: 'rgba(13, 13, 24, 0.6)', padding: '4px 10px', borderRadius: '20px', fontSize: '0.85rem', color: 'var(--text-primary)' }}
                    >
                      <div className="avatar sm" style={{ width: '22px', height: '22px', fontSize: '0.7rem' }}>
                        {m.username[0].toUpperCase()}
                      </div>
                      @{m.username}
                    </a>
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
