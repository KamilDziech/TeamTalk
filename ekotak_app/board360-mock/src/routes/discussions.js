'use strict';
/*
 * Komunikator wewnetrzny (board360 `modules/discussions`). Dyskusja = watek
 * komentarzy JEDNEGO zadania: wywolanie kogos przez @ w komentarzu wciaga to
 * zadanie do jego skrzynki, a odpowiedz z Komunikatora zapisuje sie z powrotem
 * jako komentarz pod zadaniem. Nie ma tu drugiej, rownoleglej rozmowy.
 *
 * Tytul dyskusji to KLIENT, nie zadanie ("Nazwisko · kod deala") — ustalenia
 * 2026-09-01, patrz `ekotak-app/docs/tasks/wywolanie-w-komentarzu.md`.
 *
 * Watki deal-level z panelu (`/discussions/deal/:id`) sa tu pominiete: mobilka
 * ich nie wola, a w board360 celowo nie wchodza do skrzynki.
 */

const express = require('express');
const { uuid, nowIso } = require('../crypto');
const { requireAuth, requirePermission, unprocessable } = require('../middleware');
const {
  db,
  taskById,
  userLabel,
  userById,
  discussionLabel,
  recordMentions,
  markDiscussionRead,
} = require('../store');

const router = express.Router();

const commentView = (orgId, row, userId) => ({
  id: row.id,
  body: row.body,
  authorId: row.authorId,
  authorName: userLabel(userById(orgId, row.authorId)) || '—',
  createdAt: row.createdAt,
  mine: row.authorId === userId,
});

const commentsOf = (orgId, taskId) =>
  db.taskComments
    .filter((c) => c.taskId === taskId && c.organizationId === orgId)
    .sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt));

const lastReadAt = (orgId, userId, taskId) => {
  const row = db.discussionReads.find(
    (r) => r.organizationId === orgId && r.userId === userId && r.taskId === taskId,
  );
  return row ? row.lastReadAt : null;
};

/** Dyskusje, w ktorych user bierze udzial: wywolany LUB sam komentowal. */
function myDiscussions(orgId, userId) {
  const mentioned = new Set(
    db.taskCommentMentions
      .filter((m) => m.organizationId === orgId && m.userId === userId)
      .map((m) => m.taskId),
  );
  const authored = new Set(
    db.taskComments.filter((c) => c.organizationId === orgId && c.authorId === userId).map((c) => c.taskId),
  );

  const rows = [...new Set([...mentioned, ...authored])]
    .map((taskId) => taskById(orgId, taskId))
    .filter(Boolean)
    .map((task) => {
      const comments = commentsOf(orgId, task.id);
      const last = comments.length ? comments[comments.length - 1] : null;
      const read = lastReadAt(orgId, userId, task.id);
      return {
        ...discussionLabel(orgId, task),
        taskId: task.id,
        taskTitle: task.title,
        lastComment: last ? commentView(orgId, last, userId) : null,
        commentCount: comments.length,
        unreadCount: comments.filter(
          (c) => c.authorId !== userId && (!read || new Date(c.createdAt) > new Date(read)),
        ).length,
        mentionedMe: mentioned.has(task.id),
      };
    });

  rows.sort((a, b) => {
    const ta = a.lastComment ? a.lastComment.createdAt : '';
    const tb = b.lastComment ? b.lastComment.createdAt : '';
    return tb.localeCompare(ta);
  });
  return rows;
}

// ── Skrzynka ─────────────────────────────────────────────────────────────────
router.get('/discussions', requireAuth, requirePermission('tasks.view'), (req, res) =>
  res.json(myDiscussions(req.user.organizationId, req.user.id)),
);

/** Licznik do plakietki/dzwonka — trasa PRZED `/:taskId`. */
router.get('/discussions/unread-count', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const count = myDiscussions(req.user.organizationId, req.user.id).reduce(
    (n, d) => n + d.unreadCount,
    0,
  );
  res.json({ count });
});

router.get('/discussions/:taskId', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const task = taskById(req.user.organizationId, req.params.taskId);
  if (!task) return res.status(404).json({ message: 'Dyskusja nie istnieje.' });
  return res.json({
    ...discussionLabel(req.user.organizationId, task),
    taskId: task.id,
    taskTitle: task.title,
    comments: commentsOf(req.user.organizationId, task.id).map((c) =>
      commentView(req.user.organizationId, c, req.user.id),
    ),
  });
});

router.post('/discussions/:taskId/read', requireAuth, requirePermission('tasks.view'), (req, res) => {
  markDiscussionRead(req.user.organizationId, req.user.id, req.params.taskId);
  res.status(204).end();
});

/** Cofniecie odczytu — kasuje znacznik, cudze komentarze znow sa nieprzeczytane. */
router.post('/discussions/:taskId/unread', requireAuth, requirePermission('tasks.view'), (req, res) => {
  db.discussionReads = db.discussionReads.filter(
    (r) =>
      !(
        r.organizationId === req.user.organizationId &&
        r.userId === req.user.id &&
        r.taskId === req.params.taskId
      ),
  );
  res.status(204).end();
});

/** Odpowiedz z Komunikatora = komentarz pod zadaniem (ta sama tabela). */
router.post('/discussions/:taskId/comments', requireAuth, requirePermission('tasks.view'), (req, res) => {
  const task = taskById(req.user.organizationId, req.params.taskId);
  if (!task) return res.status(404).json({ message: 'Dyskusja nie istnieje.' });
  const body = typeof (req.body || {}).body === 'string' ? req.body.body.trim() : '';
  if (!body) return unprocessable(res, 'Tresc komentarza jest wymagana.', ['tresc komentarza']);

  const row = {
    id: uuid(),
    organizationId: req.user.organizationId,
    taskId: task.id,
    authorId: req.user.id,
    body,
    createdAt: nowIso(),
  };
  db.taskComments.push(row);
  recordMentions(req.user.organizationId, task.id, row.id, req.user.id, (req.body || {}).mentions);
  markDiscussionRead(req.user.organizationId, req.user.id, task.id);
  task.commentCount = db.taskComments.filter((c) => c.taskId === task.id).length;
  task.updatedAt = nowIso();
  return res.status(201).json(commentView(req.user.organizationId, row, req.user.id));
});

module.exports = router;
