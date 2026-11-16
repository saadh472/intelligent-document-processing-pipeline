import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, CircularProgress, IconButton, InputAdornment, Paper, Stack, TextField, Typography } from '@mui/material';
import DescriptionOutlinedIcon from '@mui/icons-material/DescriptionOutlined';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined';
import { AuthSession, authService, onSessionExpired } from '../services/api';
import { webSocketService } from '../services/websocket';

type WorkspaceAuth = { username: string; signOut: () => Promise<void>; signingOut: boolean; signOutError: string };
const AuthContext = createContext<WorkspaceAuth>({ username: '', signOut: async () => {}, signingOut: false, signOutError: '' });
export const useWorkspaceAuth = () => useContext(AuthContext);

function messageFrom(error: any, fallback: string) {
  if (error?.response?.status === 401) return 'The username or password is incorrect.';
  return error?.response?.data?.message || fallback;
}

/** No document component is mounted until the server has confirmed the session. */
const AuthWorkspace: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [checking, setChecking] = useState(true);
  const [connectionError, setConnectionError] = useState('');
  const [notice, setNotice] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loginError, setLoginError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState('');
  const submitInFlight = useRef(false);
  const mounted = useRef(true);
  const authGeneration = useRef(0);

  const checkSession = useCallback(async () => {
    const generation = ++authGeneration.current;
    setChecking(true);
    setConnectionError('');
    try {
      const current = await authService.session();
      if (mounted.current && generation === authGeneration.current) setSession(current);
    } catch {
      if (mounted.current && generation === authGeneration.current) setConnectionError('We could not reach the workspace. Check that the backend is running, then try again.');
    } finally {
      if (mounted.current && generation === authGeneration.current) setChecking(false);
    }
  }, []);

  useEffect(() => {
    mounted.current = true;
    void checkSession();
    const unsubscribe = onSessionExpired(() => {
      authGeneration.current++;
      setChecking(false);
      setSession(null);
      setPassword('');
      setNotice('Your session has expired. Sign in again to continue. Your saved documents are still in the workspace.');
      webSocketService.disconnect();
    });
    return () => {
      mounted.current = false;
      authGeneration.current++;
      unsubscribe();
      webSocketService.disconnect();
    };
  }, [checkSession]);

  useEffect(() => {
    if (!session?.authenticated) {
      webSocketService.disconnect();
      return;
    }
    // Recheck on return to the tab, including a session invalidated elsewhere.
    const refresh = () => {
      if (document.visibilityState === 'hidden') return;
      const generation = authGeneration.current;
      void authService.session().then(current => {
        if (!mounted.current || generation !== authGeneration.current) return;
        if (!current.authenticated) setNotice('Your session has ended. Sign in again to continue.');
        setSession(current);
      }).catch(() => { /* A temporary network failure is not a logout. */ });
    };
    window.addEventListener('focus', refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => { window.removeEventListener('focus', refresh); window.clearInterval(timer); };
  }, [session?.authenticated]);

  const signIn = async (event: React.FormEvent) => {
    event.preventDefault();
    if (submitInFlight.current) return;
    submitInFlight.current = true;
    setSubmitting(true);
    setLoginError('');
    const generation = ++authGeneration.current;
    try {
      const current = await authService.login(username.trim(), password);
      if (mounted.current && generation === authGeneration.current) {
        setSession(current);
        setPassword('');
        setNotice('');
      }
    } catch (error) {
      if (mounted.current) setLoginError(messageFrom(error, 'Sign-in could not be completed. Check your connection and try again.'));
    } finally {
      submitInFlight.current = false;
      if (mounted.current) setSubmitting(false);
    }
  };

  const signOut = async () => {
    if (submitInFlight.current) return;
    submitInFlight.current = true;
    setSigningOut(true);
    setSignOutError('');
    const generation = ++authGeneration.current;
    try {
      const current = await authService.logout();
      if (mounted.current && generation === authGeneration.current) {
        setSession(current);
        setNotice('You have signed out securely.');
      }
      webSocketService.disconnect();
    } catch (error) {
      if (mounted.current) setSignOutError(messageFrom(error, 'Could not sign out. Check your connection and try again.'));
    } finally {
      submitInFlight.current = false;
      if (mounted.current) setSigningOut(false);
    }
  };

  if (checking || connectionError) return (
    <Box className="auth-shell">
      <Paper className="auth-card" elevation={0}>
        <DescriptionOutlinedIcon color="primary" sx={{ fontSize: 40 }} />
        <Typography component="h1" variant="h5" sx={{ mt: 2 }}>Document workspace</Typography>
        {checking ? <Stack direction="row" alignItems="center" spacing={2} sx={{ mt: 3 }} role="status"><CircularProgress size={22} /><Typography>Checking your session…</Typography></Stack> : <>
          <Alert severity="error" sx={{ mt: 3 }}>{connectionError}</Alert>
          <Button variant="contained" onClick={() => void checkSession()} sx={{ mt: 2 }}>Retry connection</Button>
        </>}
      </Paper>
    </Box>
  );

  if (session?.authenticated) return (
    <AuthContext.Provider value={{ username: session.username || 'Workspace user', signOut, signingOut, signOutError }}>
      {children}
    </AuthContext.Provider>
  );

  return (
    <Box className="auth-shell">
      <Box className="auth-intro">
        <Box className="auth-brand"><DescriptionOutlinedIcon /><span>Document workspace</span></Box>
        <Typography component="h1" variant="h3" sx={{ fontWeight: 700, letterSpacing: '-0.04em', lineHeight: 1.15 }}>From documents<br />to structured insight.</Typography>
        <Typography sx={{ mt: 3, color: 'text.secondary', maxWidth: 390, fontSize: '1.05rem', lineHeight: 1.8 }}>Upload, extract, validate, and classify documents in one workspace. Follow every processing stage as it happens.</Typography>
        <Stack direction="row" spacing={1} sx={{ mt: 4, alignItems: 'center', color: 'primary.dark' }}><LockOutlinedIcon fontSize="small" /><Typography variant="body2">Authenticated access · Persistent document storage</Typography></Stack>
      </Box>
      <Paper component="section" className="auth-card" elevation={0} aria-labelledby="sign-in-title">
        <Typography variant="overline" color="primary" sx={{ fontWeight: 700, letterSpacing: '.12em' }}>Your workspace</Typography>
        <Typography id="sign-in-title" component="h2" variant="h4" sx={{ mt: 1, fontWeight: 700 }}>Welcome back</Typography>
        <Typography color="text.secondary" sx={{ mt: 1, mb: 3 }}>Sign in to manage your documents.</Typography>
        {notice && <Alert severity="info" sx={{ mb: 2 }}>{notice}</Alert>}
        {loginError && <Alert severity="error" sx={{ mb: 2 }} role="alert">{loginError}</Alert>}
        <Box component="form" onSubmit={signIn}>
          <Stack spacing={2.5}>
            <TextField label="Username" name="username" autoComplete="username" value={username} onChange={event => setUsername(event.target.value)} required fullWidth autoFocus disabled={submitting} inputProps={{ maxLength: 100 }} />
            <TextField label="Password" name="password" autoComplete="current-password" type={showPassword ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} required fullWidth disabled={submitting} InputProps={{ endAdornment: <InputAdornment position="end"><IconButton aria-label={showPassword ? 'Hide password' : 'Show password'} onClick={() => setShowPassword(current => !current)} edge="end">{showPassword ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}</IconButton></InputAdornment> }} />
            <Button type="submit" variant="contained" size="large" disabled={submitting || !username.trim() || !password} startIcon={submitting ? <CircularProgress size={18} color="inherit" /> : undefined} sx={{ minHeight: 48 }}>{submitting ? 'Signing in…' : 'Sign in to workspace'}</Button>
          </Stack>
        </Box>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 3, lineHeight: 1.7 }}>Use the account configured for this installation. Ask the workspace owner if you need access.</Typography>
        <Box component="details" sx={{ mt: 2, color: 'text.secondary', fontSize: '.8rem', lineHeight: 1.7 }}>
          <summary style={{ cursor: 'pointer', color: '#1e40af' }}>Local setup help</summary>
          The default username is <code>admin</code>. The backend startup log identifies the generated password file. If configured, <code>IDP_USERNAME</code> and <code>IDP_PASSWORD</code> set the account instead.
        </Box>
      </Paper>
    </Box>
  );
};

export default AuthWorkspace;
