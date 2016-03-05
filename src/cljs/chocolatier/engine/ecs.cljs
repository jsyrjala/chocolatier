(ns chocolatier.engine.ecs
  (:require [chocolatier.utils.logging :as log]
            [chocolatier.engine.events :as ev]
            [clojure.set :refer [subset?]]))

;; Gameloop:   recursive function that calls all systems in a scene
;;
;; Scenes:     collection of system functions called by the game loop
;;
;; Systems:    functions that operates on a components and returns
;;             updated game state.
;;
;; Entities:   unique IDs that have a list of components to
;;             participate in
;;
;; Components: hold state and lists of functions relating to a certain aspect.
;;
;; State:      stores state for components, entities, and systems
;;
;; Using one state hashmap should be performant enough even though it
;; creates a new copy on every change to state in the game loop due to
;; structural sharing


(defn mk-scene
  "Add or update existing scene in the game state. A scene is a
   collection of systems. system-ids are a collection of keywords referencing
   a system by their unique ID."
  [state uid system-ids]
  (assoc-in state [:scenes uid] system-ids))

(def scene-id-path
  [:game :scene-id])

(defn mk-current-scene
  "Sets the current scene of the game"
  [state scene-id]
  (assoc-in state scene-id-path scene-id))

(defn mk-renderer
  [state renderer stage]
  (assoc-in state [:game :rendering-engine] {:renderer renderer :stage stage}))

(defn get-system-fns
  "Return system functions with an id that matches system-ids in order.
   If a key is not found it will not be returned."
  [state system-ids]
  (let [systems (:systems state)]
    (doall (map systems system-ids))))

(defn entities-with-component
  "Returns a set of all entity IDs that have the specified component-id"
  [state component-id]
  (get-in state [:components component-id :entities] #{}))

(defn entities-with-multi-components
  "Returns a set of all entity IDs that have all the specified component-ids."
  [state component-ids]
  (let [component-set (set component-ids)]
    ;; Iterate through all the entities and only accumulate the ones
    ;; that have all the required component IDs
    (reduce (fn [accum [entity-id entity-component-set]]
              (if ^boolean (subset? component-set entity-component-set)
                (conj accum entity-id)
                accum))
            #{}
            (:entities state))))

(defn get-component
  [state component-id]
  (get-in state [:components component-id]))

(defn get-component-fn
  [state component-id]
  (or (get-in state [:components component-id :fn])
      (throw (js/Error. (str "No component function found for " component-id)))))

(defn get-component-state
  "Returns a hashmap of state associated with the component for the given
   entity. NOTE: As a convenience, if state is not found it returns an empty
   hashmap."
  [state component-id entity-id]
  (get-in state [:state component-id entity-id] {}))

(defn get-all-component-state
  [state component-id]
  (get-in state [:state component-id]))

(defn mk-component-state
  "Returns an updated hashmap with component state for the given entity"
  [state component-id entity-id val]
  (assoc-in state [:state component-id entity-id] val))

(defn update-component-state-and-events
  "Update a components state. If there are events then also add those to
  game state."
  ([state component-id entity-id val]
   (mk-component-state state component-id entity-id val))
  ([state component-id entity-id val events]
   (-> state
       (mk-component-state component-id entity-id val)
       (ev/emit-events events))))

(defn mk-component
  "Returns an updated state hashmap with the given component.

   Args:
   - state: global state hashmap
   - uid: unique identifier for this component
   - fn-spec: [f {<opts>}] or f

   Supported component options:
   - subscriptions: a collection of selectors of messages to receive.
     This will be included as a sequence in the context passed to the
     component fn in the :inbox key
   - select-components: a collection of component IDs of additional state
     to select which will be available in the :select-components key of
     the context passed to the component fn
   - cleanup-fn: called when removing the entity and all it's components.
     This should perform any other cleanup or side effects needed to remove
     the component and all of it's state completely"
  [state uid fn-spec]
  (let [opts (when (seqable? fn-spec) (second fn-spec))
        {:keys [cleanup-fn subscriptions select-components]} opts]
    (update-in state [:components uid]
               merge
               {:fn (if opts (first fn-spec) fn-spec)
                :subscriptions subscriptions
                :select-components select-components
                :cleanup-fn cleanup-fn})))

(defn concat-keywords [k1 k2]
  (keyword (str (name k1) "-" (name k2))))

(defn select-component-states
  [hm state entity-id select-component-ids]
  ;; If the component is a vector then the first is the component-id
  ;; and the second is the entity-id. They will appear in the context
  ;; under :<component-id>-<entity-id>
  (into hm (map (fn mapselcomps [component]
                  (if (vector? component)
                    (let [[component-id entity-id] component]
                      [(concat-keywords component-id entity-id)
                       (get-component-state state
                                            component-id
                                            entity-id)])
                    [component
                     (get-component-state state
                                          component
                                          entity-id)])))
        select-component-ids))

(defn get-component-context
  "Returns a hashmap of context for use with a component fn."
  [state entity-id component]
  (let [{:keys [subscriptions select-components]} component]
    (cond-> {}
      subscriptions (assoc :inbox
                           (ev/get-subscribed-events
                            state
                            ;; Implicitely add the
                            ;; entity ID to the end of
                            ;; the selectors, this
                            ;; ensures messages are
                            ;; per entity
                            (doall (map #(vector % entity-id) subscriptions))))
      select-components (select-component-states state entity-id select-components))))

(defn system-next-state [state component-id]
  (let [entity-ids (entities-with-component state component-id)
        component-states (get-all-component-state state component-id)
        component (get-component state component-id)
        component-fn (:fn component)
        event-accum (transient [])]
    [(assoc-in
      state
      [:state component-id]
      (into {}
            (map
             (fn mapincompstate [entity-id]
               (let [component-state (get component-states entity-id)
                     context (get-component-context state
                                                    entity-id
                                                    component)
                     next-comp-state (component-fn entity-id
                                                   component-state
                                                   context)]
                 ;; If the component function returns a vector
                 ;; then accumulate the events
                 (if (vector? next-comp-state)
                   (let [[next-comp-state events] next-comp-state]
                     (doseq [e events]
                       (conj! event-accum e))
                     [entity-id next-comp-state])
                   [entity-id next-comp-state]))))
            entity-ids))
     (persistent! event-accum)]))

(defn mk-system-fn
  "Returns a function representing a system that takes a single argument for
   game state."
  [component-id]
  (fn [state]
    (let [[next-state events] (system-next-state state component-id)]
      (if (seq events)
        (ev/emit-events next-state events)
        next-state))))

(defn mk-system
  "Add the system function to the state. Optionally the third argument
   can be

   Args:
   - uid: The unique identifier for the system
   - component-id: A keyword of the component-id the system works on

   Optional:
   - f: A function that takes state as the only argument and must
     return an updated state.

   Example:
   ;; Add a system named :s1 that operates on entities that have the
   ;; :c1 component
   (mk-system :s1 :c1 my-component-fn)
   ;; Add a system named :s1 that uses our own function
   (mk-system :s1 f)"
  ([state uid system-fn]
   (log/debug "mk-system:" uid)
   (assoc-in state [:systems uid] system-fn))
  ([state uid component-id component-spec]
   (log/debug "mk-system:" uid "component-id:" component-id)
   (-> state
       (assoc-in [:systems uid] (mk-system-fn component-id))
       (mk-component component-id component-spec))))

(defn component-state-from-spec
  "Returns a function that returns an updated state with component state
   generated for the given entity-id. If no initial component state is given,
   it will default to an empty hashmap."
  [entity-id]
  (fn [state spec]
    (if ^boolean (vector? spec)
      (let [[component-id component-state] spec]
        (-> state
            (update-in [:entities entity-id]
                       #(conj (or % #{}) component-id))
            (update-in [:components component-id :entities]
                       #(conj (or % #{}) entity-id))
            (mk-component-state component-id entity-id component-state)))
      (-> state
          (update-in [:entities entity-id]
                     #(conj (or % #{}) spec))
          (update-in [:components spec :entities]
                     #(conj (or % #{}) entity-id))
          (mk-component-state spec entity-id {})))))

(defn mk-entity
  "Adds entity with uid that has component-ids into state. Optionally pass
   in init state and it will be merged in

   Component specs:
   A collection of component IDs and/or 2 item vectors of the component ID
   and hashmap of component-state.
   e.g [[:moveable {:x 0 :y 0}] :controllable]

   Example:
   Create an entity with id :player1 with components
   (mk-entity {}
              :player1
              [:controllable
               [:collidable {:hit-radius 10}]
               [:moveable {:x 0 :y 0}]])"
  [state uid components]
  (reduce (component-state-from-spec uid) state components))

(defn rm-entity-from-component-index
  [state entity-id components]
  (reduce (fn [state component-id]
            (update-in state [:components component-id :entities]
                       #(set (remove #{entity-id} %))))
          state
          components))

(defn rm-entity
  "Remove the specified entity and return updated game state"
  [state uid]
  (let [components (get-in state [:entities uid])]
    (as-> state $
      ;; Call cleanup function for the component if it's there
      (reduce #(if-let [f (get-in %1 [:components %2 :cleanup-fn])]
                 (f %1 uid)
                 %1)
              $
              components)
      (reduce #(update-in %1 [:state %2] dissoc uid) $ components)
      ;; Remove the entity
      (update-in $ [:entities] dissoc uid)
      ;; Remove the entity from component index
      (rm-entity-from-component-index $ uid components))))