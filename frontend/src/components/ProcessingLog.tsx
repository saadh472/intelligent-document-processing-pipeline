import React, { useEffect, useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  List,
  ListItem,
  Chip,
  Alert,
  CircularProgress,
  Avatar,
  LinearProgress,
  Divider,
  IconButton,
  Tooltip,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import InfoIcon from '@mui/icons-material/Info';
import WarningIcon from '@mui/icons-material/Warning';
import RefreshIcon from '@mui/icons-material/Refresh';
import ClearIcon from '@mui/icons-material/Clear';
import { ProcessingEvent, Document } from '../types/document';
import { webSocketService } from '../services/websocket';

interface ProcessingLogProps {
  selectedDocument: Document | null;
}

const ProcessingLog: React.FC<ProcessingLogProps> = ({ selectedDocument }) => {
  const [events, setEvents] = useState<ProcessingEvent[]>([]);
  const [connectionState, setConnectionState] = useState<'disconnected' | 'connecting' | 'connected' | 'reconnecting'>('disconnected');

  useEffect(() => {
    // Connect WebSocket
    webSocketService.connect();

    // Subscribe to connection state changes
    const unsubscribe = webSocketService.onConnectionStateChange((state) => {
      setConnectionState(state);
    });

    // Subscribe to all events
    const handleEvent = (event: ProcessingEvent) => {
      setEvents((prev) => [event, ...prev].slice(0, 100)); // Keep last 100 events
    };

    webSocketService.subscribeToAll(handleEvent);

    // Subscribe to specific document if selected
    if (selectedDocument?.id) {
      webSocketService.subscribeToDocument(selectedDocument.id, handleEvent);
    }

    return () => {
      unsubscribe();
    };
  }, [selectedDocument]);

  const getEventIcon = (eventType: string) => {
    switch (eventType) {
      case 'COMPLETED':
        return <CheckCircleIcon />;
      case 'ERROR':
        return <ErrorIcon />;
      case 'WARNING':
        return <WarningIcon />;
      default:
        return <InfoIcon />;
    }
  };

  const getEventColor = (eventType: string) => {
    switch (eventType) {
      case 'COMPLETED':
        return 'success';
      case 'ERROR':
        return 'error';
      case 'WARNING':
        return 'warning';
      default:
        return 'info';
    }
  };

  const getConnectionStatus = () => {
    switch (connectionState) {
      case 'connected':
        return <Chip label="Connected" color="success" size="small" icon={<CheckCircleIcon />} />;
      case 'connecting':
        return <Chip label="Connecting..." icon={<CircularProgress size={16} />} size="small" />;
      case 'reconnecting':
        return <Chip label="Reconnecting..." color="warning" size="small" />;
      default:
        return <Chip label="Disconnected" color="error" size="small" icon={<ErrorIcon />} />;
    }
  };

  const formatTime = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffSecs = Math.floor(diffMs / 1000);
    const diffMins = Math.floor(diffMs / 60000);

    if (diffSecs < 10) return 'Just now';
    if (diffSecs < 60) return `${diffSecs}s ago`;
    if (diffMins < 60) return `${diffMins}m ago`;
    return date.toLocaleTimeString();
  };

  const clearEvents = () => {
    setEvents([]);
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Real-time Processing Log
        </Typography>
        <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
          {getConnectionStatus()}
          {events.length > 0 && (
            <Tooltip title="Clear log">
              <IconButton size="small" onClick={clearEvents}>
                <ClearIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Box>
      </Box>

      {connectionState === 'disconnected' && (
        <Alert
          severity="warning"
          sx={{ mb: 2, borderRadius: 2 }}
          icon={<WarningIcon />}
        >
          <Typography variant="body2" fontWeight={600}>
            WebSocket Disconnected
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Real-time updates unavailable. Make sure the backend is running on http://localhost:8080
          </Typography>
        </Alert>
      )}

      {connectionState === 'reconnecting' && (
        <Alert
          severity="info"
          sx={{ mb: 2, borderRadius: 2 }}
        >
          Attempting to reconnect to WebSocket...
        </Alert>
      )}

      {selectedDocument && (
        <Alert
          severity="info"
          sx={{ mb: 2, borderRadius: 2 }}
        >
          <Typography variant="body2">
            Showing events for: <strong>{selectedDocument.fileName}</strong>
          </Typography>
        </Alert>
      )}

      <Paper
        sx={{
          height: 500,
          overflow: 'auto',
          bgcolor: 'background.paper',
          borderRadius: 2,
          border: '1px solid',
          borderColor: 'divider',
        }}
      >
        <List dense sx={{ p: 0 }}>
          {events.length === 0 ? (
            <ListItem>
              <Box sx={{ textAlign: 'center', width: '100%', py: 4 }}>
                <InfoIcon sx={{ fontSize: 48, color: 'text.secondary', mb: 2 }} />
                <Typography variant="body1" color="text.secondary">
                  No events yet
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
                  Processing events will appear here in real-time
                </Typography>
              </Box>
            </ListItem>
          ) : (
            events.map((event, index) => (
              <React.Fragment key={index}>
                <ListItem
                  sx={{
                    py: 1.5,
                    px: 2,
                    '&:hover': {
                      bgcolor: 'action.hover',
                    },
                  }}
                >
                  <Avatar
                    sx={{
                      bgcolor: `${getEventColor(event.eventType)}.main`,
                      width: 40,
                      height: 40,
                      mr: 2,
                    }}
                  >
                    {getEventIcon(event.eventType)}
                  </Avatar>
                  <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', mb: 0.5 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
                        <Chip
                          label={event.eventType}
                          size="small"
                          color={getEventColor(event.eventType) as any}
                          sx={{ fontWeight: 600 }}
                        />
                        {event.stage && (
                          <Chip
                            label={event.stage}
                            size="small"
                            variant="outlined"
                          />
                        )}
                        {event.progressPercentage !== undefined && event.progressPercentage > 0 && (
                          <Chip
                            label={`${event.progressPercentage}%`}
                            size="small"
                            variant="outlined"
                            color="primary"
                          />
                        )}
                      </Box>
                      <Typography variant="caption" color="text.secondary">
                        {formatTime(event.timestamp)}
                      </Typography>
                    </Box>
                    <Typography variant="body2" sx={{ mb: 0.5 }}>
                      {event.message}
                    </Typography>
                    {event.progressPercentage !== undefined && event.progressPercentage > 0 && event.progressPercentage < 100 && (
                      <LinearProgress
                        variant="determinate"
                        value={event.progressPercentage}
                        sx={{
                          height: 4,
                          borderRadius: 2,
                          mt: 1,
                          bgcolor: 'grey.200',
                        }}
                      />
                    )}
                    {event.errorMessage && (
                      <Box sx={{ mt: 1, p: 1, bgcolor: 'error.50', borderRadius: 1 }}>
                        <Typography variant="caption" color="error.main" sx={{ fontWeight: 600 }}>
                          Error: {event.errorMessage}
                        </Typography>
                      </Box>
                    )}
                    {event.documentId && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>
                        Document ID: {event.documentId}
                      </Typography>
                    )}
                  </Box>
                </ListItem>
                {index < events.length - 1 && <Divider />}
              </React.Fragment>
            ))
          )}
        </List>
      </Paper>

      {events.length > 0 && (
        <Box sx={{ mt: 2, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="caption" color="text.secondary">
            Showing {events.length} event{events.length !== 1 ? 's' : ''}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Auto-refreshing every 5 seconds
          </Typography>
        </Box>
      )}
    </Box>
  );
};

export default ProcessingLog;
