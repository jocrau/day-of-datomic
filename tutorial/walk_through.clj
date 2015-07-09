;   Copyright (c) Cognitect, Inc. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(require '[datomic.api :as d])

;; BASIC SETUP
(def uri "datomic:mem://0ef3e51f-41b9-4a55-8729-f28fcebb3d0b")
(d/create-database uri)
(def conn (d/connect uri))

;; SCHEMA
@(d/transact conn
             [{:db/id                 #db/id[:db.part/db]
               :db/ident              :ex/first-name
               :db/valueType          :db.type/string
               :db/cardinality        :db.cardinality/one
               :db.install/_attribute :db.part/db}
              {:db/id                 #db/id[:db.part/db]
               :db/ident              :ex/age
               :db/valueType          :db.type/long
               :db/cardinality        :db.cardinality/one
               :db.install/_attribute :db.part/db}
              {:db/id                 #db/id[:db.part/db]
               :db/ident              :ex/address
               :db/valueType          :db.type/ref
               :db/cardinality        :db.cardinality/one
               :db.install/_attribute :db.part/db}
              {:db/id                 #db/id[:db.part/db]
               :db/ident              :ex/locality
               :db/valueType          :db.type/string
               :db/cardinality        :db.cardinality/one
               :db.install/_attribute :db.part/db}])

(def db (d/db conn))

;; ENTITIES
@(d/transact conn
             [{:db/id       #db/id[:db.part/user -2]
               :ex/locality "Amherst"}
              {:db/id         #db/id[:db.part/user]
               :ex/first-name "Jochen"
               :ex/age        44
               :ex/address    #db/id[:db.part/user -2]}])

(d/q '[:find ?e
       :in $ ?name
       :where [?e :ex/first-name ?name]]
     db "Jochen")

;; Hmm, why doesn't this find anything??!

(def person (d/entity db (d/q '[:find ?e .
                                :in $ ?name
                                :where [?e :ex/first-name ?name]]
                              db "Jochen")))

person

(:ex/first-name person)

(-> person :ex/address)

(d/touch (-> person :ex/address))

(-> person :ex/address)

(-> person :ex/address :ex/locality)

(def address (d/entity db (d/q '[:find ?e .
                                 :where [?e :ex/locality]]
                               db)))

(:ex/_address address)

(-> (:ex/_address address) first d/touch)

@(d/transact conn
             [{:ex/first-name "Jochen"
               :ex/last-name  "Rau"
               :ex/age        44
               :ex/address    {:db/id                  #db/id[:db.part/user]
                               :ex/locality            "Amherst"
                               :ex/administrative-area "MA"
                               :ex/post-code           "01002"
                               :ex/thoroughfare        "120 Pulpit Hill Rd."
                               :ex/premises            "Unit 23"}
               :ex/phone      [{:area-code        "413"
                                :ex/exchange-code "695"
                                :ex/local-number  "2315"
                                :ex/usage         [:private :primary]
                                :ex/media-type    :mobile
                                :ex/valid-from    #inst "2012-11-01T00:00:00+00:00"}]}])
;; This doesn’t work, either. OMG!
;; TODO Extend schema http://docs.datomic.com/schema.html
;; TODO Head over to http://docs.datomic.com/query.html and get creative

(d/delete-database uri)
(d/release conn)


;;
;; QUERIES

(require '[datomic.samples.repl :as repl])

(def conn (repl/setup-sample-db-1 (repl/scratch-conn)))

;; point in time db value
(def db (d/db conn))

; Find all entities with an email attribute.
(d/q '[:find [?e ...]
       :where [?e :user/email]]
     db)

; Only find users with "editor@example.com" as email.
(d/q '[:find [?e ...]
       :in $ ?email
       :where [?e :user/email ?email]]
     db
     "editor@example.com")

; Find all comments made by user with "editor@example.com" as email.
(d/q '[:find [?comment ...]
       :in $ ?email
       :where
       [?user :user/email ?email]
       [?comment :comment/author ?user]]
     db
     "editor@example.com")

; Use aggregator count to return count of comments instead of entity ids.
(d/q '[:find (count ?comment) .
       :in $ ?email
       :where
       [?user :user/email ?email]
       [?comment :comment/author ?user]]
     db
     "editor@example.com")

; No commentable entity has :user/email, so this will be an empty return.
(d/q '[:find (count ?comment) .
       :where
       [?comment :comment/author]
       [?commentable :comments ?comment]
       [?commentable :user/email]]
     db)

; Dropping this constraint counts all comments.
(d/q '[:find (count ?comment) .
       :where
       [?comment :comment/author]
       [?commentable :comments ?comment]]
     db)

(d/q '[:find [?attr-name ...]
       :where
       [?ref :comments]
       [?ref ?attr]
       [?attr :db/ident ?attr-name]]
     db)

(def editor (d/entity db [:user/email "editor@example.com"]))

(:user/firstName editor)

(d/touch editor)

(-> editor :comment/_author)

(->> editor :comment/_author (mapcat :comments))

(def editor-id (:db/id editor))

(def txid (d/q '[:find ?tx .
                 :in $ ?e
                 :where [?e :user/firstName _ ?tx]]
               db
               editor-id))

(d/tx->t txid)

(-> (d/entity (d/db conn) txid)
    :db/txInstant)

(def older-db (d/as-of db (dec txid)))
(:user/firstName (d/entity older-db editor-id))

(def hist (d/history db))
(->> (d/q '[:find ?tx ?v ?op
            :in $ ?e ?attr
            :where [?e ?attr ?v ?tx ?op]]
          hist
          editor-id
          (d/entid db :user/firstName))
     (sort-by first))

(d/q '[:find [?e ...]
       :where [?e :user/email]]
     [[1 :user/email "jdoe@example.com"]
      [1 :user/firstName "John"]
      [2 :user/email "jane@example.com"]])

(d/release conn)
