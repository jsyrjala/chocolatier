(ns chocolatier.engine.core
  (:use [chocolatier.utils.logging :only [debug info warn error]])
  (:require [dommy.core :as dom]
            ;; NOTE to use protocols from another ns, require them
            ;; using :as and call with c/my-component-method
            [chocolatier.engine.components :as c] 
            ;; Imports the entity records
            ;; TODO can I explicitely import just the entity? or must
            ;; I mark it with :as?
            [chocolatier.engine.entities :as e])
  (:use-macros [dommy.macros :only [node sel sel1]]))


;; TODO need a defonce so we can eval theo whole namespace more than once
;; Global game state
(def game (atom nil))

;; System example:
;; Render
;; (when (satisfies? obj renderable)
;;       (draw obj))
(def systems (atom []))

;; TODO entities should be a hashmap for easy removal from the game
;; state without having to iterate through the whole list
(def entities (atom []))
(def paused? (atom false))

;; TODO a function to inspect/capture all of the current state
;; TODO a function that takes a state hashmap and starts the game from it

(defn create-entity!
  "Create a new entity and add to the list of global entities"
  [stage img pos-x pos-y anc-x anc-y]
  (info "Creating entity" stage img pos-x pos-y anc-x anc-y)
  (let [texture (js/PIXI.Texture.fromImage img)
        sprite (js/PIXI.Sprite. texture)
        bunny (new e/Bunny (keyword (gensym)) sprite 0 0)]
    (set! (.-position.x (:sprite bunny)) pos-x)
    (set! (.-position.y (:sprite bunny)) pos-y)
    (set! (.-anchor.x (:sprite bunny)) anc-x)
    (set! (.-anchor.y (:sprite bunny)) anc-y)
    (swap! entities conj bunny)))

(defn draw
  "Renders all the things to the screen.

   Iterates through all the entities and renders them to the stage.
   Renders the stage to the screen"
  [renderer stage]
  (when-not @paused?
    (doseq [e @entities]
          (c/render e stage))
    (.render renderer stage)))

(defn tick-game
  "Tick the game by miliseconds of time"
  []
  ;; Update the global entities state atom
  (doseq [e @entities]
    (c/tick e)))

;; TODO combine the game-loop and render-loop as systems that
;; run synchronously

(defn game-loop
  "Calculate changes based on the time since last change"
  [renderer stage]
  ;; TODO Calculate the changes since the last game tick
  (when-not @paused?
    (tick-game)
    (draw renderer stage)))

(defn set-interval
  "Creates an interval loop of n calls to function f per second.
   Returns a function to cancel the interval."
  [f n]
  (.setInterval js/window f (/ 1000 n))
  #(.clearInterval f (/ 1000 n)))

(defn init-game
  "Renders the game every n seconds.
   Hashmap of game properties."
  []
  (let [;; TODO get the screen height and width
        ;; TODO allow the screen height and width to be variable?
        width (aget js/window "innerWidth")
        height (aget js/window "innerHeight")
        frame-rate 60
        stage (js/PIXI.Stage. 0x66ff99)
        renderer (js/PIXI.CanvasRenderer. width height)
        main-loop (set-interval #(game-loop renderer stage) 60)]
    (dom/append! (sel1 :body) (.-view renderer))
    ;; TODO return a function that also cleans up the canvas 
    {:renderer renderer
     :stage stage
     :end main-loop}))


;; Start, stop, reset the game. Game is stored in an atom and
;; referenced directly in these functions
(defn start-game! []
  (reset! game (init-game)))

(defn stop-game!
  "Stop the game loop and remove the canvas"
  []
  (when-not (empty? @game)
    (dom/remove! (sel1 :canvas))
    (:end @game)))

(defn reset-game!
  "End the"
  []
  (stop-game!)
  (reset! entities [])
  (start-game!)
  (create-entity! (:stage @game) "static/images/bunny.png" 500 500 0.05 0.05))
