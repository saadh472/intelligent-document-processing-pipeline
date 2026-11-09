import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { Alert, Box, Button, Chip, CircularProgress, LinearProgress, Tooltip, Typography } from '@mui/material';
import { select } from 'd3-selection';
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { documentService } from '../services/api';
import { Document, DocumentStatus } from '../types/document';

interface PipelineVisualizationProps {
  documents: Document[];
  onDocumentSelect: (doc: Document | null) => void;
  onQueueFinished: () => Promise<void>;
}

const PipelineVisualization: React.FC<PipelineVisualizationProps> = ({
  documents,
  onDocumentSelect,
  onQueueFinished,
}) => {
  const svgRef = useRef<SVGSVGElement>(null);
  const processingQueue = useRef(false);
  const [processing, setProcessing] = useState(false);
  const [queueProgress, setQueueProgress] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const stages = [
    { id: 'uploaded', label: 'Upload', status: DocumentStatus.UPLOADED, color: '#94a3b8' },
    { id: 'tokenized', label: 'Tokenize', status: DocumentStatus.TOKENIZED, color: '#3b82f6' },
    { id: 'extracted', label: 'Extract', status: DocumentStatus.EXTRACTED, color: '#8b5cf6' },
    { id: 'validated', label: 'Validate', status: DocumentStatus.VALIDATED, color: '#f59e0b' },
    { id: 'categorized', label: 'Categorize', status: DocumentStatus.CATEGORIZED, color: '#10b981' },
    { id: 'completed', label: 'Complete', status: DocumentStatus.COMPLETED, color: '#10b981' },
  ];

  const stageCounts = useMemo(() => {
    return stages.map(stage => ({
      ...stage,
      count: documents.filter(doc => doc.status === stage.status).length,
    }));
  }, [documents]);

  const totalDocuments = documents.length;
  const completedDocuments = documents.filter(doc => doc.status === DocumentStatus.COMPLETED).length;
  const overallProgress = totalDocuments > 0 ? (completedDocuments / totalDocuments) * 100 : 0;

  // Find documents ready for processing (UPLOADED status)
  const documentsToProcess = useMemo(() => {
    return documents.filter(doc => doc.status === DocumentStatus.UPLOADED);
  }, [documents]);

  const handleStartPipeline = async () => {
    if (processingQueue.current || documentsToProcess.length === 0) return;

    processingQueue.current = true;
    setProcessing(true);
    setError(null);
    setSuccess(null);
    const failures: string[] = [];
    let completed = 0;

    try {
      for (let index = 0; index < documentsToProcess.length; index++) {
        const document = documentsToProcess[index];
        setQueueProgress(`Processing ${index + 1} of ${documentsToProcess.length}`);
        onDocumentSelect(document);
        try {
          const result = await documentService.processDocument(document.id);
          if (result.status === DocumentStatus.COMPLETED) {
            completed++;
          } else {
            failures.push(`${document.fileName} (${result.errorMessage || result.status})`);
          }
        } catch (failure) {
          const detail = failure instanceof Error ? failure.message : 'Processing request failed';
          failures.push(`${document.fileName} (${detail})`);
        }
      }
      await onQueueFinished();
      if (completed) setSuccess(`Completed ${completed} of ${documentsToProcess.length} documents.`);
      if (failures.length) setError(`Could not confirm ${failures.length} document(s): ${failures.join(' • ')}`);
    } finally {
      processingQueue.current = false;
      setProcessing(false);
      setQueueProgress('');
    }
  };

  useEffect(() => {
    if (!svgRef.current) return;

    const svg = select(svgRef.current);
    svg.selectAll('*').remove();

    const width = svgRef.current.clientWidth || 1000;
    const height = 180;
    const stageWidth = width / stages.length;
    const padding = 20;

    svg.attr('width', width).attr('height', height);

    // Draw pipeline stages
    stages.forEach((stage, index) => {
      const x = index * stageWidth + stageWidth / 2;
      const y = height / 2;
      const count = stageCounts[index].count;
      const isActive = count > 0;

      // Draw circle with gradient
      const circle = svg
        .append('circle')
        .attr('cx', x)
        .attr('cy', y)
        .attr('r', 35)
        .attr('fill', isActive ? stage.color : '#e2e8f0')
        .attr('stroke', isActive ? stage.color : '#cbd5e1')
        .attr('stroke-width', isActive ? 3 : 2)
        .style('cursor', 'pointer')
        .style('transition', 'all 0.3s')
        .on('mouseover', function (this: SVGCircleElement) {
          select(this).attr('r', 40).attr('stroke-width', 4);
        })
        .on('mouseout', function (this: SVGCircleElement) {
          select(this).attr('r', 35).attr('stroke-width', isActive ? 3 : 2);
        })
        .on('click', () => {
          const docsInStage = documents.filter(doc => doc.status === stage.status);
          if (docsInStage.length > 0) {
            onDocumentSelect(docsInStage[0]);
          }
        });

      // Add count badge
      if (count > 0) {
        svg
          .append('circle')
          .attr('cx', x + 20)
          .attr('cy', y - 20)
          .attr('r', 12)
          .attr('fill', '#ef4444')
          .attr('stroke', 'white')
          .attr('stroke-width', 2);

        svg
          .append('text')
          .attr('x', x + 20)
          .attr('y', y - 20)
          .attr('text-anchor', 'middle')
          .attr('dominant-baseline', 'middle')
          .attr('fill', 'white')
          .attr('font-size', '11px')
          .attr('font-weight', 'bold')
          .text(count > 99 ? '99+' : count);
      }

      // Add stage icon/indicator
      svg
        .append('text')
        .attr('x', x)
        .attr('y', y)
        .attr('text-anchor', 'middle')
        .attr('dominant-baseline', 'middle')
        .attr('fill', isActive ? 'white' : '#94a3b8')
        .attr('font-size', '20px')
        .attr('font-weight', 'bold')
        .text(index + 1);

      // Add label
      svg
        .append('text')
        .attr('x', x)
        .attr('y', y + 65)
        .attr('text-anchor', 'middle')
        .attr('font-size', '13px')
        .attr('font-weight', isActive ? 600 : 400)
        .attr('fill', isActive ? '#1e293b' : '#64748b')
        .text(stage.label);

      // Draw connector line (except for last stage)
      if (index < stages.length - 1) {
        const nextStage = stages[index + 1];
        const nextCount = stageCounts[index + 1].count;
        const isNextActive = nextCount > 0 || isActive;

        svg
          .append('line')
          .attr('x1', x + 35)
          .attr('y1', y)
          .attr('x2', x + stageWidth - 35)
          .attr('y2', y)
          .attr('stroke', isNextActive ? '#3b82f6' : '#cbd5e1')
          .attr('stroke-width', isNextActive ? 3 : 2)
          .attr('stroke-dasharray', isNextActive ? '0' : '5,5')
          .style('transition', 'all 0.3s');
      }
    });
  }, [documents, stageCounts, stages, onDocumentSelect]);

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h5" sx={{ fontWeight: 600 }}>
          Processing Pipeline
        </Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center' }}>
          {(processing || documentsToProcess.length > 0) && (
            <Button
              variant="contained"
              color="primary"
              startIcon={processing ? <CircularProgress size={16} color="inherit" /> : <PlayArrowIcon />}
              onClick={handleStartPipeline}
              disabled={processing}
              sx={{ mr: 1 }}
            >
              {processing ? queueProgress || 'Processing…' : `Start Pipeline (${documentsToProcess.length})`}
            </Button>
          )}
          <Chip
            label={`${completedDocuments}/${totalDocuments} Completed`}
            color="success"
            size="small"
          />
          <Typography variant="body2" color="text.secondary">
            {overallProgress.toFixed(0)}% Complete
          </Typography>
        </Box>
      </Box>

      {error && (
        <Alert
          severity="error"
          sx={{ mb: 2, borderRadius: 2 }}
          onClose={() => setError(null)}
        >
          {error}
        </Alert>
      )}

      {success && (
        <Alert
          severity="success"
          sx={{ mb: 2, borderRadius: 2 }}
          onClose={() => setSuccess(null)}
        >
          {success}
        </Alert>
      )}

      <Box sx={{ mb: 2 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Overall Progress
          </Typography>
          <Typography variant="body2" fontWeight={600} color="primary.main">
            {overallProgress.toFixed(0)}%
          </Typography>
        </Box>
        <LinearProgress
          variant="determinate"
          value={overallProgress}
          sx={{
            height: 8,
            borderRadius: 4,
            bgcolor: 'grey.200',
          }}
        />
      </Box>

      <Box
        sx={{
          width: '100%',
          overflowX: 'auto',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 3,
          p: 3,
          bgcolor: 'background.paper',
        }}
      >
        <svg ref={svgRef} style={{ width: '100%', minHeight: '180px', display: 'block' }} />
      </Box>

      <Box sx={{ mt: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        {stageCounts.map((stage) => (
          <Tooltip key={stage.id} title={`${stage.count} document(s) in ${stage.label} stage`}>
            <Chip
              label={`${stage.label}: ${stage.count}`}
              size="small"
              sx={{
                bgcolor: stage.count > 0 ? stage.color : 'grey.200',
                color: stage.count > 0 ? 'white' : 'text.secondary',
                fontWeight: stage.count > 0 ? 600 : 400,
              }}
            />
          </Tooltip>
        ))}
      </Box>
    </Box>
  );
};

export default PipelineVisualization;
