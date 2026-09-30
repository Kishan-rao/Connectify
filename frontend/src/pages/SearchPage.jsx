import { useState } from 'react';
import NavBar from '../components/NavBar';
import api from '../api/axiosClient';
import '../styles/search.css';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [sentRequests, setSentRequests] = useState(new Set());

  const handleSearch = async (searchPage = 0, reset = false) => {
    if (!query.trim()) {
      setResults([]);
      return;
    }

    setLoading(true);
    try {
      const { data } = await api.get(`/api/users/search?q=${encodeURIComponent(query.trim())}&page=${searchPage}&size=15`);
      setResults(prev => reset ? data.content : [...prev, ...data.content]);
      setTotalPages(data.totalPages);
      setPage(searchPage);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleSendRequest = async (username, userId) => {
    try {
      await api.post('/api/friendships', { addresseeUsername: username });
      setSentRequests(prev => new Set(prev).add(userId));
    } catch (err) {
      console.error(err);
    }
  };

  const getButtonContent = (u) => {
    if (u.relationshipStatus === 'SELF') {
      return <span className="badge-status self">You</span>;
    }
    if (u.relationshipStatus === 'FRIENDS') {
      return <span className="badge-status friends">Friends ✓</span>;
    }
    if (u.relationshipStatus === 'PENDING_SENT' || sentRequests.has(u.id)) {
      return <span className="badge-status pending">Request Sent</span>;
    }
    if (u.relationshipStatus === 'PENDING_RECEIVED') {
      return <span className="badge-status pending">Pending Response</span>;
    }
    return (
      <button className="btn-primary" style={{ padding: '6px 14px', fontSize: '0.85rem' }} onClick={() => handleSendRequest(u.username, u.id)}>
        + Add Friend
      </button>
    );
  };

  return (
    <div className="page-layout">
      <NavBar />
      <div className="search-container">
        <div className="search-bar-card">
          <form onSubmit={(e) => { e.preventDefault(); handleSearch(0, true); }} className="search-input-wrapper">
            <input
              type="text"
              className="search-input"
              placeholder="Search users by username..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            <button type="submit" className="btn-primary" disabled={loading || !query.trim()}>
              {loading ? 'Searching...' : 'Search'}
            </button>
          </form>
        </div>

        {results.length > 0 && (
          <div className="search-results-card">
            <h3 style={{ fontSize: '1rem', color: 'var(--text-secondary)' }}>
              Search Results ({results.length})
            </h3>
            {results.map(u => (
              <div key={u.id} className="search-user-row">
                <div className="search-user-info">
                  <div className="avatar">{u.username[0].toUpperCase()}</div>
                  <div>
                    <a href={`/profile/${u.username}`} className="search-user-name">
                      @{u.username}
                    </a>
                    <div className="search-user-meta">
                      {u.friendCount} {u.friendCount === 1 ? 'friend' : 'friends'}
                    </div>
                  </div>
                </div>
                <div>{getButtonContent(u)}</div>
              </div>
            ))}

            {page + 1 < totalPages && (
              <button
                className="btn-load-more"
                onClick={() => handleSearch(page + 1, false)}
                style={{ alignSelf: 'center', marginTop: '0.5rem' }}
              >
                Load More Results
              </button>
            )}
          </div>
        )}

        {!loading && query.trim() && results.length === 0 && (
          <div className="empty-state">
            <span>🔍</span>
            <p>No users found matching "{query}".</p>
          </div>
        )}
      </div>
    </div>
  );
}
