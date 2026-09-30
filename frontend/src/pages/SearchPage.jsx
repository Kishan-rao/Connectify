import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import NavBar from '../components/NavBar';
import api from '../api/axiosClient';
import '../styles/search.css';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [loading, setLoading] = useState(false);
  const [isOpen, setIsOpen] = useState(false);
  const [selectedIndex, setSelectedIndex] = useState(-1);

  const navigate = useNavigate();
  const searchCardRef = useRef(null);
  const inputRef = useRef(null);
  const abortControllerRef = useRef(null);
  const latestQueryRef = useRef('');

  // Perform search with cancellation of previous requests
  const performSearch = useCallback(async (searchQuery) => {
    const trimmed = searchQuery.trim();
    if (!trimmed) {
      setResults([]);
      setIsOpen(false);
      setLoading(false);
      return;
    }

    // Cancel in-flight request if any
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }
    const controller = new AbortController();
    abortControllerRef.current = controller;
    latestQueryRef.current = trimmed;
    setLoading(true);

    try {
      const { data } = await api.get(
        `/api/users/search?q=${encodeURIComponent(trimmed)}&page=0&size=10`,
        { signal: controller.signal }
      );

      // Only update state if this response corresponds to the latest query
      if (latestQueryRef.current === trimmed) {
        setResults(data.content || []);
        setIsOpen(true);
      }
    } catch (err) {
      if (err.name === 'CanceledError' || err.code === 'ERR_CANCELED') {
        return; // Ignore canceled requests
      }
      console.error('Search error:', err);
      if (latestQueryRef.current === trimmed) {
        setResults([]);
      }
    } finally {
      if (latestQueryRef.current === trimmed) {
        setLoading(false);
      }
    }
  }, []);

  // Debounced search trigger (250ms debounce)
  useEffect(() => {
    const trimmed = query.trim();
    setSelectedIndex(-1);

    if (!trimmed) {
      setResults([]);
      setIsOpen(false);
      setLoading(false);
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }
      return;
    }

    setIsOpen(true);
    setLoading(true);

    const timer = setTimeout(() => {
      performSearch(trimmed);
    }, 250);

    return () => clearTimeout(timer);
  }, [query, performSearch]);

  // Click outside listener to dismiss dropdown
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (searchCardRef.current && !searchCardRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleSelectUser = (user) => {
    setIsOpen(false);
    navigate(`/profile/${user.username}`);
  };

  const handleKeyDown = (e) => {
    if (!isOpen) {
      if (e.key === 'ArrowDown' && query.trim()) {
        setIsOpen(true);
      }
      return;
    }

    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setSelectedIndex(prev => (prev < results.length - 1 ? prev + 1 : 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setSelectedIndex(prev => (prev > 0 ? prev - 1 : results.length - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (selectedIndex >= 0 && selectedIndex < results.length) {
        handleSelectUser(results[selectedIndex]);
      } else if (results.length > 0) {
        handleSelectUser(results[0]);
      }
    } else if (e.key === 'Escape') {
      e.preventDefault();
      setIsOpen(false);
    }
  };

  const handleFormSubmit = (e) => {
    e.preventDefault();
    if (selectedIndex >= 0 && selectedIndex < results.length) {
      handleSelectUser(results[selectedIndex]);
    } else if (results.length > 0) {
      handleSelectUser(results[0]);
    } else if (query.trim()) {
      performSearch(query.trim());
      setIsOpen(true);
    }
  };

  return (
    <div className="page-layout">
      <NavBar />
      <div className="search-container">
        <div className="search-card-wrapper" ref={searchCardRef}>
          <div className="search-bar-card">
            <form onSubmit={handleFormSubmit} className="search-input-wrapper">
              <input
                ref={inputRef}
                type="text"
                className="search-input"
                placeholder="Search users by username..."
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                onKeyDown={handleKeyDown}
                onFocus={() => { if (query.trim()) setIsOpen(true); }}
                autoComplete="off"
                aria-autocomplete="list"
                aria-expanded={isOpen}
              />
              <button
                type="submit"
                className="btn-primary"
                disabled={loading || !query.trim()}
              >
                {loading ? 'Searching...' : 'Search'}
              </button>
            </form>
          </div>

          {/* Live Autocomplete Dropdown */}
          {isOpen && query.trim().length > 0 && (
            <div className="search-dropdown" role="listbox">
              {loading && results.length === 0 ? (
                <div className="search-dropdown-loading">
                  Searching for "{query.trim()}"...
                </div>
              ) : results.length === 0 ? (
                <div className="search-dropdown-empty">
                  No users found
                </div>
              ) : (
                <div className="search-dropdown-list">
                  {results.map((u, idx) => (
                    <div
                      key={u.id}
                      className={`search-dropdown-item ${idx === selectedIndex ? 'selected' : ''}`}
                      onClick={() => handleSelectUser(u)}
                      onMouseEnter={() => setSelectedIndex(idx)}
                      role="option"
                      aria-selected={idx === selectedIndex}
                    >
                      <div className="avatar sm">{u.username?.[0]?.toUpperCase() || '?'}</div>
                      <div className="search-dropdown-info">
                        <span className="search-dropdown-username">@{u.username}</span>
                        <span className="search-dropdown-meta">
                          {u.friendCount || 0} {u.friendCount === 1 ? 'friend' : 'friends'}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
