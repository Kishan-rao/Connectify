import { useState, useEffect } from 'react';
import NavBar from '../components/NavBar';
import api from '../api/axiosClient';
import '../styles/friends.css';

export default function FriendsPage() {
  const [friends, setFriends] = useState([]);
  const [pending, setPending] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [addUsername, setAddUsername] = useState('');
  const [message, setMessage] = useState('');

  const loadAll = async () => {
    try {
      const [friendsRes, pendingRes, suggestionsRes] = await Promise.all([
        api.get('/api/friendships/friends'),
        api.get('/api/friendships/pending'),
        api.get('/api/friendships/suggestions'),
      ]);
      setFriends(friendsRes.data);
      setPending(pendingRes.data);
      setSuggestions(suggestionsRes.data);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadAll(); }, []);

  const sendRequest = async (e) => {
    e.preventDefault();
    try {
      await api.post('/api/friendships', { addresseeUsername: addUsername });
      setMessage(`Friend request sent to @${addUsername}!`);
      setAddUsername('');
      setTimeout(() => setMessage(''), 3000);
    } catch (err) {
      setMessage(err.response?.data?.message || 'Failed to send request.');
      setTimeout(() => setMessage(''), 3000);
    }
  };

  const respond = async (id, accept) => {
    await api.put(`/api/friendships/${id}/${accept ? 'accept' : 'reject'}`);
    loadAll();
  };

  return (
    <div className="page-layout">
      <NavBar />
      <div className="friends-container">

        {/* Add Friend */}
        <div className="friends-card">
          <h2>Add a Friend</h2>
          <form onSubmit={sendRequest} className="add-friend-form">
            <input
              type="text"
              placeholder="Enter username..."
              value={addUsername}
              onChange={e => setAddUsername(e.target.value)}
              required
            />
            <button type="submit" className="btn-primary">Send Request</button>
          </form>
          {message && <p className="friend-message">{message}</p>}
        </div>

        {/* Pending Requests */}
        {pending.length > 0 && (
          <div className="friends-card">
            <h2>Pending Requests <span className="badge">{pending.length}</span></h2>
            {pending.map(f => (
              <div key={f.id} className="friend-row">
                <div className="avatar">{f.requester.username[0].toUpperCase()}</div>
                <span>@{f.requester.username}</span>
                <div className="friend-actions">
                  <button className="btn-accept" onClick={() => respond(f.id, true)}>Accept</button>
                  <button className="btn-reject" onClick={() => respond(f.id, false)}>Decline</button>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Your Friends */}
        <div className="friends-card">
          <h2>Your Friends ({friends.length})</h2>
          {friends.length === 0 && <p className="empty-text">No friends yet. Add some!</p>}
          {friends.map(f => (
            <div key={f.id} className="friend-row">
              <div className="avatar">{f.username[0].toUpperCase()}</div>
              <span>@{f.username}</span>
            </div>
          ))}
        </div>

        {/* Friend Suggestions */}
        {suggestions.length > 0 && (
          <div className="friends-card">
            <h2>People You May Know</h2>
            {suggestions.map(s => (
              <div key={s.id} className="friend-row">
                <div className="avatar">{s.username[0].toUpperCase()}</div>
                <span>@{s.username}</span>
                <button className="btn-add" onClick={() => {
                  setAddUsername(s.username);
                  api.post('/api/friendships', { addresseeUsername: s.username }).then(loadAll);
                }}>Add</button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
