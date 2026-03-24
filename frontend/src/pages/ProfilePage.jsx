import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import NavBar from '../components/NavBar';
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
  const [profile, setProfile] = useState(null);
  const [posts, setPosts] = useState([]);
  const [loading, setLoading] = useState(true);

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

  return (
    <div className="page-layout">
      <NavBar />
      <div className="profile-container">
        <div className="profile-header-card">
          <div className="profile-avatar">{profile.username[0].toUpperCase()}</div>
          <div className="profile-info">
            <h2>@{profile.username}</h2>
            <p>{profile.email}</p>
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
        </div>

        <h3 className="posts-heading">Posts</h3>
        {posts.length === 0 && <div className="empty-state"><p>No posts yet.</p></div>}
        {posts.map(post => <PostCard key={post.id} post={post} />)}
      </div>
    </div>
  );
}
