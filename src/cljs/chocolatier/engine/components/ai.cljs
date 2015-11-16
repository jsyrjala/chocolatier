(ns chocolatier.engine.components.ai
  (:require [chocolatier.utils.logging :as log]
            [chocolatier.engine.ces :as ces]
            [chocolatier.engine.systems.events :as ev]))

(defn behavior
  [entity-id component-state
   {:keys [moveable-state
           ;; passed in from system
           player-state]}]
  (let [{player-pos-x :pos-x player-pos-y :pos-y} player-state
        {:keys [pos-x pos-y]} moveable-state
        msg {:offset-x (if (< player-pos-x pos-x) 1 -1)
             :offset-y (if (< player-pos-y pos-y) 1 -1)}
        event (ev/mk-event msg [:move-change entity-id])]
    [{} [event]]))

(defn defer-events
  "Returns a pair of new game state and events. This allows the system
  function to deal with events for post processing or batching"
  [state component-id entity-id component-state events]
  [(ces/mk-component-state state component-id entity-id component-state)
   events])
