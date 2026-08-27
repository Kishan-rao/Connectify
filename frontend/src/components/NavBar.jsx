import { useState, useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import api from '../api/axiosClient';
import '../styles/navbar.css';
import '../styles/notifications.css';

export default function NavBar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const [unreadCount, setUnreadCount] = useState(0);
  const [notifications, setNotifications] = useState([]);
  const [showNotifs, setShowNotifs] = useState(false);
  const dropdownRef = useRef(null);

  const fetchUnreadCount = async () => {
    try {
      const { data } = await api.get('/api/notifications/unread-count');
      setUnreadCount(data.unreadCount || 0);
    } catch (e) {
      // ignore
    }
  };

  const fetchNotifications = async () => {
    try {
      const { data } = await api.get('/api/notifications?page=0&size=10');
      setNotifications(data.content || []);
    } catch (e) {
      // ignore
    }
  };

  useEffect(() => {
    fetchUnreadCount();
    const interval = setInterval(fetchUnreadCount, 30000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowNotifs(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const toggleNotifs = () => {
    if (!showNotifs) {
      fetchNotifications();
    }
    setShowNotifs(prev => !prev);
  };

  const markAllAsRead = async () => {
    try {
      await api.put('/api/notifications/read-all');
      setUnreadCount(0);
      setNotifications(prev => prev.map(n => ({ ...n, read: true })));
    } catch (e) {
      // ignore
    }
  };

  const handleNotificationClick = async (notif) => {
    if (!notif.read) {
      try {
        await api.put(`/api/notifications/${notif.id}/read`);
        setUnreadCount(c => Math.max(0, c - 1));
        setNotifications(prev => prev.map(n => n.id === notif.id ? { ...n, read: true } : n));
      } catch (e) {
        // ignore
      }
    }
    setShowNotifs(false);
    if (notif.postId) {
      navigate('/feed');
    } else if (notif.friendshipId) {
      navigate('/friends');
    } else if (notif.actor?.username) {
      navigate(`/profile/${notif.actor.username}`);
    }
  };

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="navbar">
      <Link to="/feed" className="nav-brand">
        <span className="logo-icon">⚡</span> Connectify
      </Link>

      <div className="nav-links">
        <Link to="/feed" className="nav-link">Feed</Link>
        <Link to="/search" className="nav-link">Search</Link>
        <Link to="/groups" className="nav-link">Groups</Link>
        <Link to="/friends" className="nav-link">Friends</Link>

        {/* Notifications Dropdown */}
        <div className="notif-wrapper" ref={dropdownRef}>
          <button
            className="nav-link notif-btn"
            style={{ background: 'none', border: 'none', cursor: 'pointer', position: 'relative' }}
            onClick={toggleNotifs}
            aria-label="Notifications"
          >
            🔔
            {unreadCount > 0 && <span className="notif-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
          </button>

          {showNotifs && (
            <div className="notif-dropdown">
              <div className="notif-header">
                <h3>Notifications</h3>
                {unreadCount > 0 && (
                  <button className="btn-mark-all" onClick={markAllAsRead}>Mark all read</button>
                )}
              </div>
              <div className="notif-list">
                {notifications.length === 0 ? (
                  <div style={{ padding: '1.5rem', textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
                    No notifications
                  </div>
                ) : (
                  notifications.map(n => (
                    <div
                      key={n.id}
                      className={`notif-item ${!n.read ? 'unread' : ''}`}
                      onClick={() => handleNotificationClick(n)}
                    >
                      <div className="avatar sm">{n.actor?.username?.[0]?.toUpperCase() || '?'}</div>
                      <div style={{ flex: 1 }}>
                        <div className="notif-item-text">{n.message}</div>
                        <div className="notif-item-time">
                          {new Date(n.createdAt).toLocaleDateString('en-US', {
                            month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
                          })}
                        </div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

        <Link to={`/profile/${user?.username}`} className="nav-link">
          @{user?.username}
        </Link>
        <button onClick={handleLogout} className="btn-logout">Logout</button>
      </div>
    </nav>
  );
}

