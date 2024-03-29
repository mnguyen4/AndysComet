;; Author(s):
;;   Brett van de Sande, Nicholas Vaidyanathan
;;; Copyright 2009 by Kurt Vanlehn and Brett van de Sande
;;;  This file is part of the Andes Intelligent Tutor Stystem.
;;;
;;;  The Andes Intelligent Tutor System is free software: you can redistribute
;;;  it and/or modify it under the terms of the GNU Lesser General Public 
;;;  License as published by the Free Software Foundation, either version 3 
;;;  of the License, or (at your option) any later version.
;;;
;;;  The Andes Solver is distributed in the hope that it will be useful,
;;;  but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;  GNU Lesser General Public License for more details.
;;;
;;;  You should have received a copy of the GNU Lesser General Public License
;;;  along with the Andes Intelligent Tutor System.  If not, see 
;;;  <http:;;;www.gnu.org/licenses/>.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(in-package :cl-user)

(defpackage :andes-database
  (:use :cl :json :mysql-connect)
  (:export :write-transaction :destroy :create :set-session 
	   :read-login-file
	   :get-matching-sessions :get-score
	   :old-sessions :set-old-session-start 
	   :get-start-tID :get-most-recent-tID
	   :get-state-property :get-state-properties
	   :set-state-property))
(in-package :andes-database)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;
;;;;         Send to database
;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defvar *connection* nil "connection to db")

(defparameter *debug* nil "database debug")
(defvar *db-lock* #+sbcl (sb-thread:make-mutex)
	      #+(and (not sbcl) bordeaux-threads) (bordeaux-threads:make-lock))

;; Should be "defconstant"
(defparameter *skip-db* nil "don't actually use db")

;; We previously used mysql for database connections.  However,
;; libmysql installs its own signal handler for SIGALRM, erasing the 
;; sbcl signal handler.  This prevents us from timing out hung session turns.

;; MySql drops connections that have been idle for over 8 hours.
;; We use idle-cleanup-function to periodically send a trivial query.
;; Instead, it may be better to catch the associated error, 
;; and attempt to reconnect to the database.
;; A dropped connection can be simulated by logging into MySql, and using
;; SHOW PROCESSLIST; and KILL <Id>; to drop a connection.

(defmacro with-db (&body body)
  "Excecute body with db mutex."
  `(unless *skip-db*
     (if *connection*
	 (sb-thread:with-mutex (*db-lock*) ,@body)
	 (error "No common database defined, can't continue."))))

(defun read-login-file (&optional path)
  "Read the database login file; file contains user name, password & (optional) database name."
  (with-open-file (f (or path (merge-pathnames "db_user_password" 
					       cl-user::*andes-path*))
		     :if-does-not-exist nil)
    (when f (values (read-line f) (read-line f) (read-line f nil)))))

(defun create (&key user password db host)

  (multiple-value-bind (u p d) (read-login-file)
    (setf user (or user u "root"))
    (setf password (or password p (error "No database password given.")))
    (setf db (or db d "andes3")))
  
  (unless *skip-db*
    (setf *connection* 
	  (connect :host host :user user :password password :database db))))

(defun destroy ()
  (unless *skip-db*
    (disconnect *connection*)))

(defun write-transaction (client-id input reply)
  "Record raw transaction in database."
  
  (with-db
      ;; Test that PROBLEM_ATTEMPT entry already exits or create an empty one
      ;; Generally, this will only happen if open-problem has not been called
      ;; or has failed.
      (unless 
	  (query *connection*
		 (format nil
			 "SELECT clientID FROM PROBLEM_ATTEMPT WHERE clientID = '~A'" 
			 client-id)
		 ;;:field-names nil :flatp t :result-types :auto
		 )
	(query *connection*
	       (format nil
		       "INSERT into PROBLEM_ATTEMPT (clientID) values ('~A')"
		       client-id)))
    
    ;; If a post contains no json, j-string is lisp nil and 
    ;; sql null is inserted into database.
    (query *connection* 
	   (format nil "INSERT into STEP_TRANSACTION (clientID,client,server) values ('~A',~:[null~;~:*'~A'~],~:[null~;~:*'~A'~])" 
		   client-id (make-safe-string input) 
		   (make-safe-string reply)))

    ;; Add any model updates associated with the step.
    (when webserver:*log-variable*
      ;; We consolidate turn updates (only log one per session)
      ;; to minimize size of STUDENT_STATE table.
      (let ((tID (get-start-tID client-id)))
	;; Do oldest ones first.
	(dolist (update (reverse webserver:*log-variable*))
	  (query *connection*
		 (format nil update tID)))))))

;; Escaping ' via '' follows ANSI SQL standard.
;; If the Database escapes backslashes, must also do those.
;; (In mysql, NO_BACKSLASH_ESCAPES is not set)
;; See http://lists.b9.com/pipermail/clsql-help/2005-July/000456.html
(defun make-safe-string (s)
  "Escape strings for database export."
  (and s (substitute-chars-strings 
	  s '((#\' . "''") (#\\ . "\\\\")))))

;; Taken from clsql file sql/utils.lisp (under LLGPL).
(defun substitute-chars-strings (str repl-alist)
  "Replace all instances of a chars with a string. repl-alist is an assoc
list of characters and replacement strings."
  (declare (simple-string str)
           (optimize (speed 3) (safety 0) (space 0)))
  (do* ((orig-len (length str))
        (new-string (make-string (replaced-string-length str repl-alist)))
        (spos 0 (1+ spos))
        (dpos 0))
      ((>= spos orig-len)
       new-string)
    (declare (fixnum spos dpos) (simple-string new-string))
    (let* ((c (char str spos))
           (match (assoc c repl-alist :test #'char=)))
      (declare (character c))
      (if match
          (let* ((subst (cdr match))
                 (len (length subst)))
            (declare (fixnum len)
                     (simple-string subst))
            (dotimes (j len)
              (declare (fixnum j))
              (setf (char new-string dpos) (char subst j))
              (incf dpos)))
        (progn
          (setf (char new-string dpos) c)
          (incf dpos))))))

;; Taken from clsql file sql/utils.lisp (under LLGPL).
(defun replaced-string-length (str repl-alist)
  (declare (simple-string str)
           (optimize (speed 3) (safety 0) (space 0)))
    (do* ((i 0 (1+ i))
          (orig-len (length str))
          (new-len orig-len))
         ((= i orig-len) new-len)
      (declare (fixnum i orig-len new-len))
      (let* ((c (char str i))
             (match (assoc c repl-alist :test #'char=)))
        (declare (character c))
        (when match
          (incf new-len (1- (length
                             (the simple-string (cdr match)))))))))

(defun set-session (client-id &key student problem section extra)
  "Updates transaction with session information."

  (unless client-id (error "set-session called with no client-id"))
    
  (unless (> (length extra) 0) ;treat empty string as null
    (setf extra nil))   ;drop from query if missing.
  
  (with-db    
      ;; Test that section entry already exits or create an empty one.
      (unless 
	  (query *connection*
		 (format nil
			 "SELECT classSection FROM CLASS_INFORMATION WHERE classSection = '~A'" 
			 section)
		 )
	(query *connection*
	       (format nil
		       "INSERT into CLASS_INFORMATION (classSection,description) values ('~A','unknown section')"
		       section)))
    ;; session is labeled by client-id 
    ;; This sets up entry in PROBLEM attempt for a given session.
      (query *connection*
       (format nil "INSERT into PROBLEM_ATTEMPT (clientID,userName,userproblem,userSection~:[~;,extra~]) values ('~a','~a','~A','~A'~@[,'~A'~])" 
	       extra client-id student problem section extra))))

(defun truncate-string (x)
  "Truncate arg for warning messages."
  (subseq x 0 (min (length x) 400)))

(defmacro errors-to-warnings (object &rest forms)
  "Intercept any errors, turning them into warnings, then return."
  ;; If there are json errors, we want to log them and then soldier on.
 `(handler-case (progn ,@forms)
    (error (c) (warn (format nil "~A for ~A" (type-of c) 
			     ;; The objects are generally strings and the 
			     ;; most common errors occur for very long strings.
			     (truncate-string ,object))))))

;; (andes-database:get-matching-sessions '("solution-step" "seek-help") :student "bvds" :problem "s2e" :section "1234")
;;
(defun get-matching-sessions (methods &key student problem section extra)
  "Get posts associated with the given methods from all matching previous sessions."
  
  (unless (> (length extra) 0) ;treat empty string as null.
    (setf extra nil)) ;drop from query if missing.
  
  (with-db
    (let ((result (query *connection*
			 (if (and (> (length extra) 1) (equal (subseq extra 0 2) "Q_"))
			 (format nil "SELECT server,client,STEP_TRANSACTION.clientID FROM PROBLEM_ATTEMPT,STEP_TRANSACTION WHERE userProblem='~A' AND userSection='~A' AND extra='~A' AND PROBLEM_ATTEMPT.clientID=STEP_TRANSACTION.clientID" 
				 problem section extra)
			(format nil "SELECT server,client,STEP_TRANSACTION.clientID FROM PROBLEM_ATTEMPT,STEP_TRANSACTION WHERE userName='~A' AND userProblem='~A' AND userSection='~A'~@[ AND extra='~A'~] AND PROBLEM_ATTEMPT.clientID=STEP_TRANSACTION.clientID" 
				 student problem section extra) 
			)
		   ;:flatp t
			 ))
	  ;; By default, cl-json turns camelcase into dashes:  
	  ;; Instead, we are case insensitive, preserving dashes.
	  (*json-identifier-name-to-lisp* #'string-upcase))
      
      ;; Filter out turns where the reply contains a timeout error.
      ;; Unless the bug causing the timeout has been fixed, these errors
      ;; prevent a student from reopening a problem.
      (setf result
	    (remove-if
	     #'(lambda (x)
		 ;; find client turn such that associated server
		 ;; reply does not have a timeout error.
		 (and x
		      (server-reply-has-timeout
		       ;; Actually, we only need to decode the 
		       ;; top-level list.
		       ;; Sometimes result gets truncated on very long
		       ;; backtraces.  It might be better to just search 
		       ;; the string for the timeout message?
		       (errors-to-warnings x (decode-json-from-string x)))))
	     result
	     :key #'car))
      
      ;; parse json in each member of result
      ;; pick out post and client-id
      (setf result
	    (mapcar 
	     ;; A post with no json sent gets translated into nil;
	     ;; see write-transaction.
	     #'(lambda (x) 
		 (let ((y (second x)))
		   (cons (and y 
			      (errors-to-warnings 
			       y
			     (decode-json-from-string y)))
			 (third x))))
	     result))
      
      ;; pick out the solution-step and get-help methods
      (remove-if #'(lambda (x) (not (member (cdr (assoc :method x))
					    methods
					    :test #'equal)))
		 result
		 :key #'car))))

(defun server-reply-has-timeout (reply)
  "Test whether a server reply includes a timeout error."
  (some #'(lambda (x) (and (string-equal (cdr (assoc :action x)) "log")
			   (string-equal (cdr (assoc :error-type x))
					 "timeout")))
	(cdr (assoc :result reply))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;         Acess grade
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun get-score (&key student problem section extra)
  "Get latest score."
  
  (unless (> (length extra) 0) ;treat empty string as null.
    (setf extra nil)) ;drop from query if missing.
  
  ;; Assume every session has a grade reported somewhere in it.
  ;; Thus, we only need to search the most recent session.
  ;; Only methods open-problem, solution-step, get-help, close-problem should be searched.
  ;; open-problem puts a dummy set-score=0 at the beginning, but then the value
  ;; is updated as old sessions are rerun.
  
  (with-db
    (let* ((query (format nil "SELECT clientID FROM PROBLEM_ATTEMPT WHERE userName='~A' AND userProblem='~A' AND userSection='~A'~@[ AND extra='~A'~] ORDER BY startTime DESC LIMIT 1"				
			  student problem section extra))
	   (client-id (car (car (query *connection* query))))
	   (results (when client-id
		      (query *connection*
			     (format nil "SELECT server FROM STEP_TRANSACTION WHERE clientID='~A'"
				     client-id)
					;:flatp t
			     )))
	   ;; By default, cl-json turns camelcase into dashes:  
	   ;; Instead, we are case insensitive, preserving dashes.
	   (*json-identifier-name-to-lisp* #'string-upcase))
      
      (when nil  ;; debug prints
	(unless client-id (format webserver:*stdout* "query ~S~%" 
				  query))
	(format webserver:*stdout* "db results for ~S:~%  ~S~%" 
		client-id results))
      
      ;; Go through turns in a session backwords looking for 
      ;; last set-score.
      (dolist (result (reverse results))
	;; Go through lines in a reply backwards looking for
	;; the last set-score.
	(dolist (line (reverse 
		       (cdr (assoc :result 
				   (when result 
				     (errors-to-warnings 
				      (car result)
				      (decode-json-from-string 
				       (car result))))))))
	  (when (equal (cdr (assoc :action line)) "set-score")
	    (return-from get-score 
	      (cdr (assoc :score line)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;
;;;          Access student and section customizations.
;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defun single-query (x)
  "Perform database query that expects a single column reply."
  ;; Do not use inside another with-db wrapper.
  (let ((result 
	 (mapcar #'car
		 (with-db (query *connection* x)))))
    (when nil ;debug print
      (format webserver:*stdout*
	      "database query:~%  ~A~%  ~A~%  " x result))
    result))

;; Only set these inside an old-sessions wrapper.
(defvar *disable-saving-state* nil)
(defvar *old-client-id* nil)

(defun set-old-session-start (client-id)
  "Use client-id from old session to set session tID.  Should be called inside an old-sessions wrapper."
  (setf *old-client-id* client-id))

(defmacro old-sessions (&body body)
  "Turn off writing state to database."
  `(let ((*disable-saving-state* t) *old-client-id*) ,@body))

;; It would be more efficient if we cached the results for 
;; this query.  However, it cannot be cached as a session
;; variable because write-transaction cannot access session 
;; variables.
;; A global cache would need periodic flushing.
(defun get-start-tID (client-id)
  (car (car 
	(query *connection* 
	       (format nil "SELECT MIN(tID) FROM STEP_TRANSACTION WHERE clientID='~A'"
		       client-id))))) 

(defun get-session-starting-tID ()
  "Get any existing tID associated with the start of the current session.  If client-id is a string, use that session."
      ;; Should cache result of this query.
      (with-db (get-start-tID 
		(or *old-client-id* webserver:*log-id*))))

(defun get-most-recent-tID ()
  "Get largest tID from STEP_TRANSACTION; if table is empty, create dummy step."
  (loop for i from 0 to 1
	thereis  (car (single-query "SELECT MAX(tID) FROM STEP_TRANSACTION"))
	do (format t "writing it~%") (write-transaction "_dummy_session" nil nil)))

(defun get-state-properties (&key (student session:*user*) 
			     (section session:*section*) (model "default")
			     (tID (get-session-starting-tID)))
  "Retrieve state parameters from the database.  Model includes \"default\", \"client\", or \"server\".  Returns an alist of property-value pairs.  Null student returns section-wide results."  
  (let ((properties
	 (single-query
	  ;; If student exists, still need to look for any section defaults
	  ;; for the case 
	  (format nil "SELECT DISTINCT property FROM STUDENT_STATE WHERE userSection='~A' AND (~@[userName='~A' OR ~]userName='') AND model='~A'~@[ AND tID<~A~]" 
		  section student model tID)))
	result)

    ;; Add any cached properties.
    (dolist (p session:*state-cache*)
      (when (equal model (car (car p)))
	(pushnew (cdr (car p)) properties :test #'equal)))
    
    (dolist (property properties)
      (multiple-value-bind (value flag)
	  (get-state-property property :section section :student student
			      :model model :tID tID)
	;; Remove properties that have been deleted.
	(when flag (push (cons property value) result))))
    result))

(defun get-state-property (property &key (student session:*user*) 
			   (section session:*section*) (model "default")
			   (tID (get-session-starting-tID)))
  "Retrieve state parameter from the database.  Model includes \"default\", \"client\", or \"server\".  Returns value and flag indicating a value has been found.  Null student returns section-wide results."  
  
  ;; First, see if property is cached.
  ;; Using the cache is necessary for re-running old sessions.
  ;; Cache needs to be flushed between different old sessions.
  (let ((x (assoc (cons model property) session:*state-cache* :test #'equal)))
    (when x
      (return-from get-state-property
	(values (cdr x) t))))
  
  ;; Then look in database for student-specific match.
  (when student
    (let ((student-result
	   (single-query
	    (format nil "SELECT value FROM STUDENT_STATE WHERE userSection='~A' AND userName='~A' AND model='~A' AND property='~A'~@[ AND tID<~A~] ORDER BY tID DESC LIMIT 1" 
		    section student model property tID))))
      (when (and student-result (car student-result))
	(return-from get-state-property
	  (values (read-from-string (car student-result)) t)))))
  
  ;; Look for section-wide match.
  (let ((section-result
	 (single-query
	  (format nil "SELECT value FROM STUDENT_STATE WHERE userSection='~A' AND userName='' AND model='~A' AND property='~A'~@[ AND tID<~A~] ORDER BY tID DESC LIMIT 1" 
		  section model property tID))))
    (when (and section-result (car section-result))
      (return-from get-state-property
	(values (read-from-string (car section-result)) t))))
  
  ;; Nothing found
  (values nil nil))

(defun set-state-property (property value &key 
			   (student session:*user*)
			   (section session:*section*)
			   (model "default")
			   no-store
			   tID)
  "Update a student or section state parameter.  If value is null, delete 
that parameter.  If tID is not specified, insert at end of turn; 
if it is an integer, insert directly with specified tID; 
otherwise, use latest step tID.  No-store means add to cache only."

  ;; Test that student is not empty string.
  ;; The correct way for setting up section defaults is by using nil for student.
  (when (and (stringp student) 
	     (equal (string-right-trim match:*whitespace* student) ""))
    (error 'webserver:log-error :tag 'empty-student-string 
	   :text "Null string sent for student"))

  ;; Save in cache, by either updating or pushing
  (unless tID
    (let ((x (assoc (cons model property) session:*state-cache* 
		    :test #'equal)))
      (if x
	  (setf (cdr x) value)
	  (push (cons (cons model property) value) session:*state-cache*))))

  (unless (or *disable-saving-state* no-store)
    ;; Save to STUDENT_STATE either now or later.
    (let ((query-format-string
	   (format nil "REPLACE into STUDENT_STATE (userSection,userName,model,property,tID,value) VALUES ('~A',~:[''~;~:*'~A'~],'~A','~A',~~A,~:[NULL~*~;'~A'~])"
		   section student model
		   (if (stringp property) property (prin1-to-string property))
		   ;; tID itself is passed in by the logging function.
		   value 
		   (when value (make-safe-string (prin1-to-string value))))))

      ;; If tID is specified, insert directly at that point,
      ;; else insert into beginning of session after step is completed.
      (if tID
	  (progn
	    (unless (integerp tID) (setf tID (get-most-recent-tID)))
	    ;; (format t query-format-string tID)
	    (with-db 
	      (query *connection* 
		     (format nil query-format-string tID))))
	  (push query-format-string webserver:*log-variable*)))))
