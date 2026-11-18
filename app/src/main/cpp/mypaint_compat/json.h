/* Substituto mínimo de <json.h> (json-c), apenas para o libmypaint compilar sem essa dependência.
 *
 * O libmypaint só usa JSON em mypaint_brush_from_string(). Esta app NÃO chama essa função: os ficheiros .myb
 * (JSON) são lidos em Kotlin e os valores aplicados com mypaint_brush_set_base_value / set_mapping_n /
 * set_mapping_point, que são a API pública do libmypaint. Por isso as funções abaixo nunca devolvem dados e
 * mypaint_brush_from_string() falharia se alguém a chamasse. */
#ifndef ANIMSTUDIO_JSON_STUB_H
#define ANIMSTUDIO_JSON_STUB_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct json_object json_object;
typedef enum { json_type_null, json_type_boolean, json_type_double, json_type_int, json_type_object, json_type_array, json_type_string } json_type;

#define JSON_C_MINOR_VERSION 13

static inline json_object *json_tokener_parse(const char *s) { (void)s; return 0; }
static inline json_object *json_object_new_object(void) { return 0; }
static inline void json_object_put(json_object *o) { (void)o; }
static inline int json_object_object_get_ex(json_object *o, const char *k, json_object **out) { (void)o; (void)k; (void)out; return 0; }
static inline json_object *json_object_object_get(json_object *o, const char *k) { (void)o; (void)k; return 0; }
static inline int json_object_is_type(json_object *o, json_type t) { (void)o; (void)t; return 0; }
static inline double json_object_get_double(json_object *o) { (void)o; return 0.0; }
static inline int json_object_get_int(json_object *o) { (void)o; return 0; }
static inline int json_object_array_length(json_object *o) { (void)o; return 0; }
static inline json_object *json_object_array_get_idx(json_object *o, int i) { (void)o; (void)i; return 0; }

/* Percorre zero entradas: o corpo do ciclo nunca executa. */
#define json_object_object_foreach(obj, key, val) \
    for (const char *key = 0; key != 0; key = 0) for (json_object *val = 0; val != 0; val = 0)

#ifdef __cplusplus
}
#endif

#endif /* ANIMSTUDIO_JSON_STUB_H */
